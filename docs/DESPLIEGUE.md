# Despliegue

Procedimiento para poner la aplicación en internet **a coste cero**. Tres piezas
gratuitas y separadas, según [ADR-0002](adr/ADR-0002-plataforma-de-despliegue.md):

| Pieza | Dónde | Qué hace falta |
|---|---|---|
| PostgreSQL | **Neon** | Cuenta. Sin tarjeta. 0,5 GB y 100 CU-hora al mes. |
| API Java | **Koyeb** | Cuenta. Una instancia gratuita por organización: 512 MB, 0,1 vCPU, Fráncfort. |
| PWA | **Cloudflare Pages** | Cuenta. Sin tarjeta. |

> **Por qué no Render, aunque ya haya cuenta.** Sus 750 horas de instancia al mes son
> **por workspace, no por servicio**, y un mes tiene unas 730: la bolsa da para *un*
> servicio despierto, no para varios. Y al agotarla **Render suspende todos los servicios
> gratuitos de la cuenta** hasta el mes siguiente, no sólo el que se pasó. Añadir esta
> aplicación a un workspace donde ya hay otras cosas es arriesgarse a tumbarlas. Su
> PostgreSQL gratuito, además, **caduca a los 30 días**. El razonamiento completo, en
> [ADR-0002](adr/ADR-0002-plataforma-de-despliegue.md).

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

1. Crea un proyecto en [neon.tech](https://neon.tech) con una base de datos llamada
   `gastos`.
2. Copia la cadena de conexión. Viene en formato `postgresql://…`, y Java necesita
   **JDBC**, así que hay que partirla:

   ```
   postgresql://usuario:contrasena@ep-algo.eu-central-1.aws.neon.tech/gastos?sslmode=require
   ```

   se convierte en:

   ```
   SPRING_DATASOURCE_URL      jdbc:postgresql://ep-algo.eu-central-1.aws.neon.tech/gastos?sslmode=require
   SPRING_DATASOURCE_USERNAME usuario
   SPRING_DATASOURCE_PASSWORD contrasena
   ```

   **`sslmode=require` no es opcional**: sin él la conexión iría en claro por internet.

3. Elige **`eu-central-1` (Fráncfort)**: es donde está la instancia gratuita de Koyeb.
   Cada salto entre continentes son decenas de milisegundos en *cada* consulta.

No hace falta crear ninguna tabla: **Flyway migra al arrancar**. La primera vez aplicará
las seis migraciones de golpe.

---

## 2. API en Koyeb

La instancia gratuita de Koyeb despliega **una imagen ya construida** desde un registro,
así que primero hay que publicarla. Se usa GHCR, el registro de GitHub, porque la imagen
ya se construye en CI y no hace falta otra cuenta.

### 2.1 Publicar la imagen

Desde la pestaña *Actions* del repositorio, el workflow **Desplegar** con `que = api`.
Construye `infra/Dockerfile` y la sube a `ghcr.io/<usuario>/gastos-api:main`.

Hazlo **público** la primera vez (*Packages → gastos-api → Package settings → Change
visibility*), o Koyeb no podrá descargarla sin credenciales. La imagen no contiene
secretos: son todos variables de entorno.

Para publicarla a mano, sin Actions:

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u <usuario> --password-stdin
docker build -f infra/Dockerfile -t ghcr.io/<usuario>/gastos-api:main .
docker push ghcr.io/<usuario>/gastos-api:main
```

### 2.2 Crear el servicio

En el panel de Koyeb, *Create Web Service* → *Docker image*:

| Campo | Valor |
|---|---|
| Imagen | `ghcr.io/<usuario>/gastos-api:main` |
| Región | **Frankfurt** (la gratuita) |
| Instancia | **Free** |
| Puerto | `8080`, protocolo HTTP |
| Health check | HTTP `GET /actuator/health` |
| *Grace period* del health check | **90 segundos** |

Los 90 segundos no son por exceso de prudencia: con 0,1 vCPU, arrancar Spring Boot y
aplicar las migraciones de Flyway lleva bastante más que los 40 segundos que basta darle
a una máquina normal. Con el margen justo, Koyeb mata el contenedor a mitad del arranque
y entra en un bucle de reinicios que parece un fallo de la aplicación.

### 2.3 Variables y secretos

Las que no son secretas van como *environment variables*; el resto, como **secrets** de
Koyeb. **Nunca en el repositorio.**

| Variable | Valor |
|---|---|
| `JWT_SECRET` | secreto — genéralo con `openssl rand -base64 48` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://…/gastos?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | el de Neon |
| `SPRING_DATASOURCE_PASSWORD` | secreto — el de Neon |
| `MAIL_HOST` | `smtp-relay.brevo.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | el de Brevo |
| `MAIL_PASSWORD` | secreto — la **clave SMTP** de Brevo, no la del panel |
| `MAIL_FROM` | un remitente **verificado** en Brevo, o rechaza el envío |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1` |

Detalle del correo en [OPERACION.md §3](OPERACION.md). Los cuatro que dependen del
dominio del frontend se ponen en el paso 4.

`JAVA_OPTS` ya viene en el `Dockerfile`, pero conviene ponerlo también aquí: deja a la
vista, junto al resto de la configuración, que el heap está ajustado a 512 MB.

Apunta la URL que sale (`https://gastos-api-<org>.koyeb.app`).

---

## 3. PWA en Cloudflare Pages

El frontend no se compila, pero sí hay que decirle dónde está la API:

```bash
node scripts/preparar-frontend.mjs https://gastos-api-<org>.koyeb.app
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

En las variables de entorno del servicio de Koyeb:

| Variable | Valor |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://gastos.pages.dev` |
| `WEBAUTHN_RP_ID` | `gastos.pages.dev` |
| `WEBAUTHN_ORIGINS` | `https://gastos.pages.dev` |
| `APP_BASE_URL` | `https://gastos.pages.dev` |

Koyeb redespliega el servicio al guardar. Espera a que el health check pase antes de
probar nada.

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
curl -s https://gastos-api-<org>.koyeb.app/actuator/health
```

Debe responder `{"status":"UP"}`. **La primera llamada del día puede tardar medio minuto
largo**: la instancia gratuita baja a cero tras una hora sin tráfico y una JVM con
0,1 vCPU no arranca deprisa. Si en vez de tardar responde `DOWN`, casi siempre es la base
de datos: los *logs* del servicio en Koyeb lo dicen en la primera línea del error.

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

Para la API hace dos cosas: construye la imagen y la sube a GHCR, y después pide a Koyeb
que redespliegue el servicio, que vuelve a descargar la etiqueta `main`. **La primera vez
el servicio hay que crearlo a mano** en el panel (paso 2.2): el workflow actualiza un
servicio que ya existe, no lo crea.

Secretos que hay que dar de alta en el repositorio (*Settings → Secrets and variables →
Actions*):

| Secreto | De dónde sale |
|---|---|
| `KOYEB_API_TOKEN` | Panel de Koyeb, *Organization settings → API* |
| `CLOUDFLARE_API_TOKEN` | Panel de Cloudflare, permiso *Cloudflare Pages: Edit* |
| `CLOUDFLARE_ACCOUNT_ID` | Panel de Cloudflare, en la barra lateral |

Para subir la imagen a GHCR no hace falta secreto: vale el `GITHUB_TOKEN` que el propio
workflow recibe, con permiso `packages: write`.

Los secretos de la aplicación (`JWT_SECRET`, base de datos, correo) **no** se ponen aquí:
viven en la configuración del servicio de Koyeb, y así no pasan por el registro de
ejecuciones de GitHub.

---

## Lo que cuesta y lo que duele

**Coste: 0 €.** Las tres piezas —Neon, Koyeb y Cloudflare Pages— están en su capa
gratuita, y ninguna pide tarjeta.

Lo que hay que asumir a cambio:

- **Arranque en frío, y no de dos segundos.** Koyeb baja la instancia a cero tras una
  hora sin tráfico, y levantar una JVM con 0,1 vCPU lleva su tiempo; Neon hace lo mismo
  con la base de datos a los 5 minutos, así que se suman. Cuenta con **medio minuto largo
  en la primera petición del día**. El armazón de la PWA se sirve de la caché del service
  worker, así que la aplicación *se ve* al instante aunque los datos tarden. Es lo que se
  paga por no pagar: si un día estorba, Fly.io por 1-4 € al mes lo quita.
- **Una sola instancia gratuita por organización en Koyeb.** Si más adelante hace falta
  desplegar otra cosa, no cabe en la misma cuenta gratuita.
- **Sin copia de seguridad automática.** Neon guarda historial reciente, pero eso no es
  una copia: conviene un `pg_dump` periódico ([OPERACION.md §8](OPERACION.md)).
- **Las capas gratuitas cambian.** Este mismo documento ya ha cambiado de plataforma dos
  veces. Es el motivo de que todo esté en `Dockerfile` y `docker-compose.yml`: mudarse es
  cuestión de horas, y de que lo único propio de Koyeb sea un paso de este manual.

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

Ventaja real sobre Koyeb: región `mad` en vez de Fráncfort, una vCPU compartida entera en
vez de 0,1, y por tanto un arranque en frío de segundos y no de medio minuto.

---

## Si algo va mal

| Síntoma | Casi siempre es |
|---|---|
| La aplicación carga pero el login da error de red | `CORS_ALLOWED_ORIGINS` no incluye el dominio exacto (con esquema, sin barra final). |
| El botón de passkey no hace nada | `WEBAUTHN_RP_ID` con el dominio de la API en vez del del frontend. |
| No aparece el botón de passkey | Sin HTTPS. El navegador no expone la API fuera de un contexto seguro. |
| `/actuator/health` responde `DOWN` | Base de datos: revisa `sslmode=require` y las credenciales. |
| La primera petición del día tarda muchísimo | Normal: arranque en frío de Koyeb más el de Neon. Si tarda *siempre*, es que el servicio se está reiniciando: mira los logs. |
| El servicio de Koyeb se reinicia en bucle al desplegar | *Grace period* del health check demasiado corto. Con 0,1 vCPU hacen falta 90 s (paso 2.2). |
| Koyeb no puede descargar la imagen | El paquete de GHCR sigue siendo privado. Hazlo público (paso 2.1). |
| El enlace de invitación no lleva a ninguna parte | `APP_BASE_URL` con el valor de desarrollo. El código tecleado a mano sí funciona, por eso pasa desapercibido. |
| El enlace del correo apunta a `localhost` | Falta `APP_BASE_URL`. |
| No llega ningún correo | Falta `MAIL_HOST`; la API lo avisa con un `WARN` en cada intento. |
| Tras desplegar se sigue viendo la versión vieja | Caché del navegador. El service worker va a red primero, así que basta recargar; si persiste, mira que Pages sirva `_headers`. |
