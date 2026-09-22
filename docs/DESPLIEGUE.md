# Despliegue

Procedimiento para poner la aplicación en internet **a coste cero**. Tres piezas
gratuitas y separadas, según [ADR-0002](adr/ADR-0002-plataforma-de-despliegue.md):

| Pieza | Dónde | Qué hace falta |
|---|---|---|
| PostgreSQL | **Neon** | Cuenta. Sin tarjeta. 0,5 GB y 100 CU-hora al mes. |
| API Java | **Render** | Cuenta. Web service gratuito: 512 MB, 0,1 vCPU, Fráncfort. |
| PWA | **Cloudflare Pages** | Cuenta. Sin tarjeta. |

> **La base de datos no va en Render aunque la API sí.** Su PostgreSQL gratuito son
> 256 MB y **caduca a los 30 días** de crearla: pasado ese plazo queda inaccesible hasta
> que se pague. La de Neon es gratis y no caduca.

> **Las horas de Render son por workspace, no por servicio**, y un mes tiene unas 730 de
> las 750 que da la capa gratuita. Esta aplicación consume poco —se despierta sólo cuando
> la usáis— pero comparte bolsa con todo lo demás que tengas ahí. Lee
> [«Vigilar la bolsa de horas»](#vigilar-la-bolsa-de-horas-de-render) antes de dar esto
> por montado: si se agota, Render suspende **todos** los servicios gratuitos de la
> cuenta, no sólo el que se pasó.

> **Por qué no Vercel para la API.** No ejecuta contenedores de larga vida. Sólo serviría
> para la PWA, que ya está resuelta y gratis en Cloudflare Pages.

> **Si el arranque en frío molesta**, la salida es **Fly.io** (1-4 €/mes con escalado a
> cero); `fly.toml` sigue en el repositorio para eso, y el procedimiento está al final de
> este documento. Si lo que compensa es no tener arranque en frío ninguno a cambio de
> administrar una máquina, **Oracle Cloud Always Free**.

> **Nada de esto se ha desplegado todavía.** Lo que hay en el repositorio son los ficheros
> preparados y este procedimiento, escrito leyendo las condiciones publicadas de cada
> plataforma. La primera ejecución real es la que dirá si son como se describen; hasta
> entonces el ADR-0002 sigue en estado *Propuesta*.

---

## El orden importa: hay una dependencia circular

Las dos piezas se necesitan mutuamente:

- El **frontend** necesita saber la URL de la API (`config.js` y el `connect-src` de la
  CSP).
- La **API** necesita saber el dominio del frontend (`CORS_ALLOWED_ORIGINS`,
  `WEBAUTHN_RP_ID`, `APP_BASE_URL`).

Por eso son **cuatro pasos y no tres**: se despliega la API, luego el frontend, y al
final se vuelve a la API para darle el dominio del frontend. Saltarse el cuarto paso
deja una aplicación que carga pero no puede ni hacer login.

---

## 1. Base de datos en Neon

> **Ignora el asistente de Neon si te ofrece `neon login`, `neon.ts`, `neon deploy` o
> instalar su MCP.** Todo eso es su onboarding para proyectos JavaScript. Esta aplicación
> es Java y migra con Flyway: de Neon sólo hace falta **la cadena de conexión**.

1. Crea un proyecto en [neon.tech](https://neon.tech). **La región se elige al crearlo y
   después no se puede cambiar**: `eu-central-1` (Fráncfort), la misma que el servicio de
   Render. Cada salto entre continentes son decenas de milisegundos en *cada* consulta, y
   aquí se cruzan varias por pantalla.

2. **No hace falta crear ninguna base de datos.** Neon trae una llamada `neondb` y sirve
   perfectamente; lo único que importa es usar el nombre que aparezca en la cadena.

3. En *Connect to your database*, copia la cadena. **Elige la conexión directa, no la del
   pooler** (la del pooler lleva `-pooler` en el host). El pooler existe para aplicaciones
   que abren y cierran conexiones sin parar; ésta mantiene su propio grupo de cinco y
   ejecuta las migraciones de Flyway al arrancar, que es justo el tipo de trabajo con el
   que un pooler en modo transacción da sorpresas.

4. Java necesita **JDBC**, así que hay que partirla:

   ```
   postgresql://usuario:contrasena@ep-algo.eu-central-1.aws.neon.tech/neondb?sslmode=require
   ```

   se convierte en:

   ```
   SPRING_DATASOURCE_URL      jdbc:postgresql://ep-algo.eu-central-1.aws.neon.tech/neondb?sslmode=require
   SPRING_DATASOURCE_USERNAME usuario
   SPRING_DATASOURCE_PASSWORD contrasena
   ```

   Fíjate en las tres diferencias: delante va `jdbc:`, el usuario y la contraseña **salen
   de la URL** y pasan a sus propias variables, y el resto se queda igual.

   **`sslmode=require` no es opcional**: sin él la conexión iría en claro por internet.

> **Esa cadena lleva la contraseña de la base de datos dentro.** Va directa del panel de
> Neon al de Render y a ningún otro sitio: ni a un fichero del repositorio, ni a un chat,
> ni a un correo. Si alguna vez se escapa, en Neon se puede restablecer la contraseña del
> rol sin rehacer el proyecto.

No hace falta crear ninguna tabla: **Flyway migra al arrancar**. La primera vez aplicará
las siete migraciones de golpe, y en los logs de Render se ve una línea por cada una.

> **Neon sirve PostgreSQL 18** y el proyecto está verificado contra 16. El esquema es SQL
> estándar a propósito —`uuid`, `numeric`, `varchar`, `timestamp`, `boolean`, `date`, nada
> propietario— así que no debería notarse. Lo que sí importaba es la versión de Flyway:
> la que gestiona Spring Boot 3.5.6 es anterior a PostgreSQL 18, y Flyway comprueba la
> versión del servidor al arrancar. Por eso el `pom.xml` la fija explícitamente.

---

## 2. API en Render

Render construye la imagen desde `infra/Dockerfile` y despliega desde el repositorio: no
hay que publicar nada en ningún registro.

### 2.1 Crear el servicio desde el Blueprint

`render.yaml`, en la raíz, ya trae la configuración: región, plan gratuito, ruta del
Dockerfile, health check y la lista de variables.

1. En el panel de Render, *New → Blueprint*.
2. Conecta el repositorio y elige la rama.
3. Render lee `render.yaml` y **pide los valores marcados como `sync: false`**. Los de la
   base de datos y el correo se ponen aquí; los cuatro del dominio del frontend se dejan
   en blanco de momento y se rellenan en el paso 4.

`JWT_SECRET` no lo pide: `render.yaml` le dice que lo genere él. Nadie llega a verlo, que
es lo suyo.

### 2.2 Qué esperar del primer despliegue

Dos cosas que asustan si no se avisan:

- **La construcción tarda.** Es un `mvn clean package` de seis módulos dentro del
  contenedor. La primera vez, sin caché, cuenta varios minutos.
- **El arranque, también.** Spring Boot y las migraciones de Flyway con 0,1 vCPU pasan del
  minuto. Hasta que `/actuator/health` responde, Render muestra el servicio como no
  disponible. **Parece colgado y no lo está.** Si a los cinco minutos sigue igual, mira
  los logs: casi siempre es la base de datos.

### 2.3 Variables y secretos

Están todas declaradas en `render.yaml`, y ahí se explica para qué sirve cada una. Las
que hay que rellenar a mano:

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://…/neondb?sslmode=require` (paso 1) |
| `SPRING_DATASOURCE_USERNAME` | el de Neon |
| `SPRING_DATASOURCE_PASSWORD` | el de Neon |
| `MAIL_HOST` | **déjalo en blanco** si aún no tienes Brevo |
| `MAIL_USERNAME` | en blanco |
| `MAIL_PASSWORD` | en blanco |
| `MAIL_FROM` | en blanco |

**El correo es opcional y se puede añadir después.** Con `MAIL_HOST` en blanco la
aplicación arranca igual; lo único que no funciona es «he olvidado mi contraseña», y la
API lo avisa con un `WARN` en cada intento. Lo que **no** hay que hacer es dejar puesto el
servidor de correo sin credenciales detrás: entonces la aplicación cree que puede enviar
y falla al intentarlo.

Cuando montes Brevo se rellenan las cuatro —`MAIL_HOST` es `smtp-relay.brevo.com`,
`MAIL_PASSWORD` la **clave SMTP** y no la del panel, y `MAIL_FROM` un remitente
**verificado**— y Render redespliega solo. Detalle en [OPERACION.md §3](OPERACION.md).

Apunta la URL que sale (`https://gastos-api.onrender.com`).

### Vigilar la bolsa de horas de Render

La capa gratuita da **750 horas de instancia al mes por workspace**, compartidas entre
todos los servicios gratuitos, y un mes tiene unas 730. Un servicio sólo gasta mientras
está despierto, y se duerme a los **15 minutos** sin tráfico.

Esta aplicación gasta poco: con dos personas abriéndola unas cuantas veces al día son
**del orden de 15 h al mes**, contando la cola de 15 minutos de cada uso. Lo que hay que
vigilar no es esto, es el conjunto:

- **Un servicio que alguien mantiene despierto a propósito** —un *ping* periódico para
  evitar el arranque en frío— gasta las horas de la ventana que se le haya puesto. De
  8:30 a 21:00 todos los días son unas 390 h al mes. Uno así cabe; dos, no.
- **Un rastreador indexando una web pública** la mantiene despierta sin que nadie lo
  pida, y eso no aparece en ninguna previsión.

Y el castigo es colectivo: **al agotar la bolsa, Render suspende todos los servicios
gratuitos del workspace** hasta el mes siguiente, no sólo el que se pasó.

Míralo de vez en cuando en *Billing → Usage*. Si el mes va camino de las 750, lo barato
es mover **esta** aplicación, que es la que menos gasta y la única que no tiene público:
`fly.toml` está preparado para eso.

## 3. PWA en Cloudflare Pages

El frontend no se compila, pero sí hay que decirle dónde está la API:

```bash
node scripts/preparar-frontend.mjs https://gastos-api.onrender.com
```

Eso deja en `dist/` una copia del frontend con `config.js` y el `connect-src` de la CSP
ya apuntando a la API. **Los dos a la vez**: cambiar sólo uno produce una aplicación que
llama al servidor correcto y cuyo navegador bloquea la respuesta, sin que nada en los
logs de la API lo delate.

```bash
npx wrangler pages deploy dist --project-name=gastos
```

Apunta la URL que sale (`https://gastos.pages.dev`).

> **No conectes el repositorio a Pages con construcción automática.** Publicaría
> `frontend/` tal cual, con la configuración de desarrollo dentro. O se sube `dist/`, o
> se usa el workflow de despliegue, que hace lo mismo.

---

## 4. Volver a la API con el dominio del frontend

En *Environment* del servicio de Render:

| Variable | Valor |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://gastos.pages.dev` |
| `WEBAUTHN_RP_ID` | `gastos.pages.dev` |
| `WEBAUTHN_ORIGINS` | `https://gastos.pages.dev` |
| `APP_BASE_URL` | `https://gastos.pages.dev` |

Render redespliega al guardar. Espera a que el health check pase antes de probar nada:
con 0,1 vCPU tarda más de un minuto.

Cuatro avisos sobre estos valores:

- **`WEBAUTHN_RP_ID` es el dominio del frontend, sin esquema ni puerto.** Poner aquí el
  de la API hace que el navegador rechace las passkeys **sin dar ninguna explicación**:
  el botón parece no hacer nada.
- **Cambiar de dominio más adelante invalida todas las passkeys registradas.** Están
  atadas al `rp-id`. Si vas a poner dominio propio, ponlo antes de dar de alta ninguna.
- **`APP_BASE_URL` es la URL a la que apunta el enlace de recuperación de contraseña.**
  Con el valor de desarrollo, el correo llega con un enlace a `localhost`.
- **`APP_BASE_URL` es también el enlace de invitación de la pareja** (`joinUrl`). Si
  apunta a `localhost`, el código se genera bien pero el enlace que se comparte no lleva
  a ninguna parte. El código tecleado a mano seguiría funcionando, así que el fallo pasa
  desapercibido hasta que alguien intenta usar el enlace.

---

## 5. Comprobar que ha salido bien

```bash
curl -s https://gastos-api.onrender.com/actuator/health
```

Debe responder `{"status":"UP"}`. **La primera llamada del día puede tardar uno o dos
minutos**: la instancia gratuita se duerme a los 15 minutos sin tráfico y una JVM con
0,1 vCPU no arranca deprisa. Si en vez de tardar responde `DOWN`, casi siempre es la base
de datos: los *logs* del servicio en Render lo dicen en la primera línea del error.

Luego, desde el navegador, en `https://gastos.pages.dev`:

1. **Carga la pantalla de acceso** → la API responde y la CSP es correcta.
2. **Entra con la contraseña** → CORS bien configurado. Si falla aquí con un error de
   red y en la consola aparece algo de CORS, revisa `CORS_ALLOWED_ORIGINS`.
3. **Tu cuenta → Añadir passkey** → `WEBAUTHN_RP_ID` y `WEBAUTHN_ORIGINS` bien.
4. **He olvidado mi contraseña** → mira que llegue el correo y que el enlace apunte al
   dominio real y no a `localhost`.
5. **Tu cuenta → Invitar a tu pareja → Generar código** → comprueba que el `joinUrl` que
   se comparte lleva al dominio real. Ábrelo en otro navegador: debe decir a qué hogar
   entras y quién invita.
6. **Instálala en el móvil** («Añadir a pantalla de inicio») y repite el punto 3 desde
   ahí.

La lista completa de cosas que revisar antes de exponerlo está en
[OPERACION.md §9](OPERACION.md).

---

## Desplegar desde GitHub Actions

`.github/workflows/deploy.yml` hace los pasos 2 y 3 desde la pestaña *Actions*. Es
**manual a propósito**: hoy `main` sigue en el commit inicial y nada se publica solo.
Cuando haya un despliegue funcionando y se decida que `main` es producción, basta añadir
`push: branches: [main]` al disparador.

Para la API sólo llama al *deploy hook* de Render, que es lo que dispara la construcción
y el despliegue. `render.yaml` lleva `autoDeploy: false` justo para que ese disparo sea
deliberado y no cada push. **La primera vez el servicio se crea desde el Blueprint** en el
panel (paso 2.1); el workflow redespliega uno que ya existe.

Secretos que hay que dar de alta en el repositorio (*Settings → Secrets and variables →
Actions*):

| Secreto | De dónde sale |
|---|---|
| `RENDER_DEPLOY_HOOK_URL` | Panel de Render, servicio → *Settings → Deploy Hook* |
| `CLOUDFLARE_API_TOKEN` | Panel de Cloudflare, permiso *Cloudflare Pages: Edit* |
| `CLOUDFLARE_ACCOUNT_ID` | Panel de Cloudflare, en la barra lateral |

El *deploy hook* es una URL con un testigo dentro: quien la tenga puede disparar un
despliegue, así que va como secreto y no como variable.

Los secretos de la aplicación (`JWT_SECRET`, base de datos, correo) **no** se ponen aquí:
viven en la configuración del servicio de Render, y así no pasan por el registro de
ejecuciones de GitHub.

---

## Lo que cuesta y lo que duele

**Coste: 0 €.** Las tres piezas —Neon, Render y Cloudflare Pages— están en su capa
gratuita.

Lo que hay que asumir a cambio:

- **Arranque en frío, y no de dos segundos.** Render duerme el servicio a los 15 minutos
  sin tráfico, y levantar Spring Boot con 0,1 vCPU lleva su tiempo; Neon hace lo mismo
  con la base de datos a los 5 minutos, así que se suman. Cuenta con **uno o dos minutos
  en la primera petición del día**, y con volver a pagarlo si dejáis la aplicación
  aparcada un cuarto de hora. El armazón de la PWA se sirve de la caché del service
  worker, así que la aplicación *se ve* al instante aunque los datos tarden. Es lo que se
  paga por no pagar: si un día estorba, Fly.io por 1-4 € al mes lo quita.
- **La bolsa de horas es compartida** con lo demás que haya en el workspace, y agotarla
  suspende todo. Ver [«Vigilar la bolsa de horas»](#vigilar-la-bolsa-de-horas-de-render).
- **Sin copia de seguridad automática.** Neon guarda historial reciente, pero eso no es
  una copia: conviene un `pg_dump` periódico ([OPERACION.md §8](OPERACION.md)).
- **Las capas gratuitas cambian.** Este mismo documento ya ha cambiado de plataforma
  varias veces. Es el motivo de que todo esté en `Dockerfile` y `docker-compose.yml`:
  mudarse es cuestión de horas, y de que lo único propio de Render sea `render.yaml` y un
  paso de este manual.

### Si prefieres pagar y no esperar: Fly.io

`fly.toml` sigue en el repositorio, ajustado a 512 MB con escalado a cero. El
procedimiento es el mismo cambiando el paso 2:

```bash
fly auth login
fly launch --no-deploy --copy-config --name gastos-api --region mad
fly secrets set JWT_SECRET="$(openssl rand -base64 48)" SPRING_DATASOURCE_URL="…" SPRING_DATASOURCE_USERNAME="…" SPRING_DATASOURCE_PASSWORD="…" MAIL_HOST="smtp-relay.brevo.com" MAIL_PORT="587" MAIL_USERNAME="…" MAIL_PASSWORD="…" MAIL_FROM="gastos@tudominio.es"
fly deploy
```

`--no-deploy` importa: primero los secretos, o el primer arranque falla sin `JWT_SECRET`,
y eso es intencionado ([OPERACION.md §1](OPERACION.md)). Fly reinicia solo al cambiar un
secreto, así que el paso 4 es `fly secrets set` en lugar de tocar el panel.

Ventaja real sobre Render: región `mad` en vez de Fráncfort, una vCPU compartida entera
en vez de 0,1 —y por tanto un arranque en frío de segundos, no de minutos— y una bolsa
que no comparte con nada.

**Koyeb** es la otra salida a coste cero, si lo que estorba es compartir bolsa: una
instancia gratuita por organización, 512 MB y 0,1 vCPU en Fráncfort, y duerme **a la
hora** en vez de a los 15 minutos, así que se notan menos los arranques en frío. A cambio
sólo despliega imágenes ya construidas, así que habría que publicarla antes en GHCR.

---

## Si algo va mal

| Síntoma | Casi siempre es |
|---|---|
| La aplicación carga pero el login da error de red | `CORS_ALLOWED_ORIGINS` no incluye el dominio exacto (con esquema, sin barra final). |
| El botón de passkey no hace nada | `WEBAUTHN_RP_ID` con el dominio de la API en vez del del frontend. |
| No aparece el botón de passkey | Sin HTTPS. El navegador no expone la API fuera de un contexto seguro. |
| `/actuator/health` responde `DOWN` | Base de datos: revisa `sslmode=require` y las credenciales. |
| La primera petición del día tarda muchísimo | Normal: arranque en frío de Render más el de Neon. Si tarda *siempre*, es que el servicio se está reiniciando: mira los logs. |
| El despliegue parece colgado y no acaba | Casi siempre está arrancando: con 0,1 vCPU, Spring Boot y Flyway pasan del minuto antes de que responda el health check (paso 2.2). |
| Todos los servicios gratuitos suspendidos a la vez | Se agotaron las 750 h del workspace. No es un fallo de esta aplicación: es la bolsa compartida. |
| El enlace de invitación no lleva a ninguna parte | `APP_BASE_URL` con el valor de desarrollo. El código tecleado a mano sí funciona, por eso pasa desapercibido. |
| El enlace del correo apunta a `localhost` | Falta `APP_BASE_URL`. |
| No llega ningún correo | Falta `MAIL_HOST`; la API lo avisa con un `WARN` en cada intento. |
| Tras desplegar se sigue viendo la versión vieja | Caché del navegador. El service worker va a red primero, así que basta recargar; si persiste, mira que Pages sirva `_headers`. |
