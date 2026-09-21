# Despliegue

Procedimiento para poner la aplicación en internet. Tres piezas gratuitas y
separadas, según [ADR-0002](adr/ADR-0002-plataforma-de-despliegue.md):

| Pieza | Dónde | Qué hace falta |
|---|---|---|
| PostgreSQL | **Neon** | Cuenta. Sin tarjeta. |
| API Java | **Fly.io** | Cuenta **con tarjeta**, y **de pago**: ver el aviso de abajo. |
| PWA | **Cloudflare Pages** | Cuenta. Sin tarjeta. |

> **Aviso (21/09/2026): Fly.io ya no tiene capa gratuita para cuentas nuevas** desde
> octubre de 2024; sólo una prueba de 2 horas o 7 días. Con escalado a cero se paga sólo
> mientras la máquina está encendida, del orden de 1 a 4 € al mes para este uso, pero no
> es gratis. Las alternativas gratuitas siguen siendo Koyeb (una instancia de 512 MB y
> 0,1 vCPU en Frankfurt) y Oracle Cloud Always Free (2 OCPU y 12 GB desde junio de 2026,
> administrando uno mismo la máquina). Ver ADR-0002.

> Nada de esto se ha desplegado todavía. Lo que hay en el repositorio son los ficheros
> preparados (`fly.toml`, `scripts/preparar-frontend.mjs`, el workflow de despliegue) y
> este procedimiento. La primera ejecución real es la que dirá si las capas gratuitas
> siguen siendo como se describen; hasta entonces el ADR-0002 sigue en estado *Propuesta*.

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

3. Elige una región cercana a la de Fly (`mad` → `eu-central-1`). Cada salto entre
   continentes son decenas de milisegundos en *cada* consulta.

No hace falta crear ninguna tabla: **Flyway migra al arrancar**. La primera vez aplicará
las cuatro migraciones de golpe.

---

## 2. API en Fly.io

```bash
fly auth login
```

```bash
fly launch --no-deploy --copy-config --name gastos-api --region mad
```

`--no-deploy` es importante: primero hay que poner los secretos, o el primer arranque
fallará sin `JWT_SECRET` (y eso es intencionado, ver [OPERACION.md §1](OPERACION.md)).

### Secretos

Los secretos van en `fly secrets`, **no** en `fly.toml` ni en el repositorio:

```bash
fly secrets set JWT_SECRET="$(openssl rand -base64 48)" SPRING_DATASOURCE_URL="jdbc:postgresql://…/gastos?sslmode=require" SPRING_DATASOURCE_USERNAME="…" SPRING_DATASOURCE_PASSWORD="…" MAIL_HOST="smtp-relay.brevo.com" MAIL_PORT="587" MAIL_USERNAME="…" MAIL_PASSWORD="…" MAIL_FROM="gastos@tudominio.es"
```

`MAIL_PASSWORD` es la **clave SMTP** de Brevo, no la contraseña del panel, y `MAIL_FROM`
tiene que ser un remitente **verificado** o Brevo rechaza el envío. Detalle en
[OPERACION.md §3](OPERACION.md).

Los tres que dependen del dominio del frontend se ponen en el paso 4.

```bash
fly deploy
```

Apunta la URL que sale al final (`https://gastos-api.fly.dev`).

---

## 3. PWA en Cloudflare Pages

El frontend no se compila, pero sí hay que decirle dónde está la API:

```bash
node scripts/preparar-frontend.mjs https://gastos-api.fly.dev
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

```bash
fly secrets set CORS_ALLOWED_ORIGINS="https://gastos.pages.dev" WEBAUTHN_RP_ID="gastos.pages.dev" WEBAUTHN_ORIGINS="https://gastos.pages.dev" APP_BASE_URL="https://gastos.pages.dev"
```

Fly reinicia la aplicación sola al cambiar un secreto.

Tres avisos sobre estos cuatro valores:

- **`WEBAUTHN_RP_ID` es el dominio del frontend, sin esquema ni puerto.** Poner aquí el
  de la API hace que el navegador rechace las passkeys **sin dar ninguna explicación**:
  el botón parece no hacer nada.
- **Cambiar de dominio más adelante invalida todas las passkeys registradas.** Están
  atadas al `rp-id`. Si vas a poner dominio propio, ponlo antes de dar de alta ninguna.
- **`APP_BASE_URL` es la URL a la que apunta el enlace de recuperación de contraseña.**
  Con el valor de desarrollo, el correo llega con un enlace a `localhost`.

---

## 5. Comprobar que ha salido bien

```bash
curl -s https://gastos-api.fly.dev/actuator/health
```

Debe responder `{"status":"UP"}`. Si dice `DOWN`, casi siempre es la base de datos:
`fly logs` lo dice en la primera línea del error.

Luego, desde el navegador, en `https://gastos.pages.dev`:

1. **Carga la pantalla de acceso** → la API responde y la CSP es correcta.
2. **Entra con la contraseña** → CORS bien configurado. Si falla aquí con un error de
   red y en la consola aparece algo de CORS, revisa `CORS_ALLOWED_ORIGINS`.
3. **Tu cuenta → Añadir passkey** → `WEBAUTHN_RP_ID` y `WEBAUTHN_ORIGINS` bien.
4. **He olvidado mi contraseña** → mira que llegue el correo y que el enlace apunte al
   dominio real y no a `localhost`.
5. **Instálala en el móvil** («Añadir a pantalla de inicio») y repite el punto 3 desde
   ahí.

La lista completa de cosas que revisar antes de exponerlo está en
[OPERACION.md §9](OPERACION.md).

---

## Desplegar desde GitHub Actions

`.github/workflows/deploy.yml` hace los pasos 2 y 3 desde la pestaña *Actions*. Es
**manual a propósito**: hoy `main` sigue en el commit inicial y nada se publica solo.
Cuando haya un despliegue funcionando y se decida que `main` es producción, basta añadir
`push: branches: [main]` al disparador.

Secretos que hay que dar de alta en el repositorio (*Settings → Secrets and variables →
Actions*):

| Secreto | De dónde sale |
|---|---|
| `FLY_API_TOKEN` | `fly tokens create deploy` |
| `CLOUDFLARE_API_TOKEN` | Panel de Cloudflare, permiso *Cloudflare Pages: Edit* |
| `CLOUDFLARE_ACCOUNT_ID` | Panel de Cloudflare, en la barra lateral |

Los secretos de la aplicación (`JWT_SECRET`, base de datos, correo) **no** se ponen aquí:
viven en `fly secrets` y así no pasan por el registro de ejecuciones de GitHub.

---

## Lo que cuesta y lo que duele

**Coste:** Neon y Cloudflare Pages, 0 €. Fly.io, de pago por uso: entre 1 y 4 € al mes
con escalado a cero. Si tiene que ser 0 €, la API va en Koyeb o en Oracle Cloud (ADR-0002).

Lo que hay que asumir a cambio:

- **Arranque en frío.** La API escala a cero; la primera petición tras horas de
  inactividad tarda unos segundos. Neon hace lo mismo con la base de datos, así que se
  suman. Para uso doméstico es aceptable; el armazón de la PWA se sirve de la caché del
  service worker, así que la aplicación *se ve* al instante aunque los datos tarden.
- **Sin copia de seguridad automática.** Neon guarda historial reciente, pero eso no es
  una copia: conviene un `pg_dump` periódico ([OPERACION.md §8](OPERACION.md)).
- **Las capas gratuitas cambian.** Es el motivo de que todo esté en `Dockerfile` y
  `docker-compose.yml`: mudarse a Oracle Cloud Free Tier o a Koyeb es cuestión de horas.

---

## Si algo va mal

| Síntoma | Casi siempre es |
|---|---|
| La aplicación carga pero el login da error de red | `CORS_ALLOWED_ORIGINS` no incluye el dominio exacto (con esquema, sin barra final). |
| El botón de passkey no hace nada | `WEBAUTHN_RP_ID` con el dominio de la API en vez del del frontend. |
| No aparece el botón de passkey | Sin HTTPS. El navegador no expone la API fuera de un contexto seguro. |
| `/actuator/health` responde `DOWN` | Base de datos: revisa `sslmode=require` y las credenciales. |
| El enlace del correo apunta a `localhost` | Falta `APP_BASE_URL`. |
| No llega ningún correo | Falta `MAIL_HOST`; la API lo avisa con un `WARN` en cada intento. |
| Tras desplegar se sigue viendo la versión vieja | Caché del navegador. El service worker va a red primero, así que basta recargar; si persiste, mira que Pages sirva `_headers`. |
