# Manual de operación

Lo que hay que saber para poner esto en marcha y no llevarse sustos. No es documentación
de arquitectura: son las cosas que te afectan como quien administra la instalación.

---

## 1. El secreto de firma: sin él la aplicación no arranca

`JWT_SECRET` no tiene valor por defecto, **y es intencionado**. Una clave de firma
escrita en el código es una clave pública: quien la conozca puede fabricarse un token
para cualquier hogar y entrar como quien quiera.

**Generar una clave** (mínimo 32 caracteres):

```bash
export JWT_SECRET=$(openssl rand -base64 48)
```

En Windows, con PowerShell:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
```

| Entorno | De dónde sale |
|---|---|
| Tests | Clave fija en `application-test.yml`. No hace falta hacer nada. |
| `docker compose` local | Valor por defecto en `infra/docker-compose.yml`, marcado como sólo-desarrollo. |
| Cualquier cosa accesible desde red | **Generada por ti** y pasada por variable de entorno. Nunca en el repositorio. |

**Cambiar la clave invalida todas las sesiones.** Los tokens firmados con la anterior
dejan de verificarse y hay que volver a iniciar sesión. Con dos usuarios es una molestia
de diez segundos, pero conviene saberlo antes de que pase.

---

## 2. Cuentas de usuario: no hay registro abierto

El alta inicial (`POST /api/v1/auth/register`) **sólo funciona mientras no exista ningún
hogar**. En cuanto creas el tuyo, ese endpoint devuelve `422` para siempre.

A partir de ahí:

- **Sólo el `OWNER`** —quien creó el hogar— puede dar de alta al segundo conviviente,
  con `POST /api/v1/auth/members`.
- Un `MEMBER` que lo intente recibe `403`.
- **El hogar admite dos miembros como máximo.** El tercero se rechaza con `422`.

Es una instalación doméstica para dos personas: dejar el registro abierto sería regalar
una cuenta a cualquiera que encuentre la URL.

### Comprobar el estado de una instalación

```bash
curl http://localhost:8080/api/v1/auth/status
```

`{"needsBootstrap": true}` significa que todavía no hay hogar y el alta inicial está
disponible.

---

## 3. Correo saliente (Brevo)

El restablecimiento de contraseña envía un enlace por correo a través de un **relé
SMTP**. Se eligió SMTP y no la API REST del proveedor porque es un contrato universal:
cambiar de Brevo a otro proveedor son tres líneas de configuración, no reescribir código.

| Variable | Valor con Brevo |
|---|---|
| `MAIL_HOST` | `smtp-relay.brevo.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | El login SMTP que da Brevo |
| `MAIL_PASSWORD` | La **clave SMTP**, no la contraseña del panel |
| `MAIL_FROM` | Un remitente **verificado** en Brevo, o el envío se rechaza |
| `APP_BASE_URL` | La URL pública del frontend, a la que apunta el enlace |

**Sin `MAIL_HOST` la aplicación arranca igual**, pero no envía nada. Avisa con un `WARN`
al arrancar y en cada intento:

```
No hay servidor de correo configurado (spring.mail.host). El restablecimiento de
contrasena NO enviara ningun mensaje. Configuralo antes de desplegar.
```

Ese aviso existe a propósito. Sin él, alguien desplegaría sin correo, el formulario
respondería correctamente —porque **no puede** distinguir casos, para no delatar qué
cuentas existen— y nadie se enteraría de que ningún enlace llega a su destino.

El enlace **no se escribe nunca en el log**: sería una credencial válida en texto plano
al alcance de cualquiera que lea las trazas.

---

## 4. Si olvidas la contraseña

1. En la pantalla de acceso, **«He olvidado mi contraseña»**.
2. Escribe tu correo. La respuesta es la misma exista o no la cuenta.
3. Abre el enlace del correo. **Caduca en 30 minutos y sólo sirve una vez.**
4. Elige una contraseña nueva.

Al restablecerla **se cierran todas las sesiones**, también en otros dispositivos. Es
deliberado: quien necesita recuperar el acceso es porque lo había perdido, y dejar vivas
las sesiones anteriores dejaría dentro a quien no debe.

Pedir el enlace dos veces **invalida el anterior**: nunca hay dos puertas abiertas.

Para cambiarla sabiéndola, hay pantalla propia en **Tu cuenta**, desde el pie de la
navegación. Pide la actual aunque la sesión sea válida, para que quien encuentre el
equipo desbloqueado no pueda apoderarse de la cuenta.

---

## 4 bis. Passkeys: entrar con la huella

Una passkey sustituye a escribir la contraseña. Lo que guarda el servidor es sólo la
**clave pública**: la privada no sale nunca del dispositivo, así que ni siquiera alguien
con toda la base de datos podría suplantarte.

**No sustituye a la contraseña**, convive con ella. Perder el móvil no puede dejarte
fuera de las cuentas de casa, y aquí no hay soporte al que llamar.

### Darla de alta

1. **Tu cuenta**, desde el pie de la navegación.
2. En **Passkeys**, ponle un nombre con el que reconocer el aparato (`iPhone de Javi`).
3. **Añadir passkey** y confirma con la huella, la cara o el PIN.

Para entrar, luego, basta con **«Entrar con passkey»** en la pantalla de acceso: no hay
que escribir el correo.

### Dos parámetros que hay que acertar al desplegar

| Variable | Qué es | Ejemplo |
|---|---|---|
| `WEBAUTHN_RP_ID` | **Dominio del frontend**, no el de la API | `gastos.example.com` |
| `WEBAUTHN_ORIGINS` | Direcciones completas, con esquema y puerto | `https://gastos.example.com` |

**El error clásico es poner en `WEBAUTHN_RP_ID` el dominio de la API.** Si la aplicación
está en `gastos.example.com` y la API en `api.example.com`, el valor es `gastos.example.com`
(o `example.com`). Con el valor equivocado el navegador rechaza la ceremonia **sin decir
por qué**: el botón parece no hacer nada.

Si no se define `WEBAUTHN_ORIGINS`, se toma el valor de `CORS_ALLOWED_ORIGINS`, que en la
práctica son las mismas direcciones.

### Lo que hay que saber

- **Fuera de `localhost` hace falta HTTPS.** No es una recomendación: sin contexto seguro
  el navegador ni siquiera ofrece la API. Por eso el botón se oculta en lugar de fallar.
- **Cambiar de dominio invalida todas las passkeys.** Están atadas al `rp-id`; si mañana
  la aplicación se muda, hay que volver a darlas de alta. La contraseña sigue valiendo,
  que para eso está.
- Cada persona ve y borra **sólo las suyas**, aunque compartáis hogar.
- La lista dice si la passkey está **copiada en tu cuenta del dispositivo** o **sólo en
  este dispositivo**. Piénsalo dos veces antes de borrar una de las segundas si es la
  única que te queda.
- Se exige verificación de usuario (huella, cara o PIN). Un móvil desbloqueado encima de
  una mesa no abre las cuentas.

---

## 5. Contraseñas: entre 12 y 128 caracteres

**No se exigen mayúsculas, dígitos ni símbolos**, y es deliberado. Las reglas de
composición empujan a la gente hacia patrones predecibles del tipo `Verano2026!`, que un
atacante prueba de los primeros. La longitud es lo que de verdad encarece un ataque por
fuerza bruta.

También hay un **máximo de 128**: sin él, una contraseña enorme convertiría cada intento
de login en trabajo de CPU gratis para quien ataca.

Se guardan con **BCrypt de coste 12**, en una tabla aparte de los usuarios, y nunca se
devuelven por la API.

---

## 6. Poner en marcha desde cero

```bash
docker compose -f infra/docker-compose.yml up --build
```

Con la aplicación arrancada, crear el hogar (una sola vez):

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"householdName":"Nuestra casa","email":"tu@correo.es","displayName":"Tu nombre","password":"una-contrasena-larga-y-decente","monthlyNetIncome":2200.00}'
```

Devuelve ya la sesión iniciada. Guarda el `accessToken` para las siguientes llamadas y el
`refreshToken` para renovarla.

Después, dar de alta al segundo conviviente:

```bash
curl -X POST http://localhost:8080/api/v1/auth/members \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <accessToken>' \
  -d '{"email":"otro@correo.es","displayName":"Conviviente","password":"otra-contrasena-larga","monthlyNetIncome":1800.00}'
```

---

## 7. Sesiones

| | Duración | Se puede revocar |
|---|---|---|
| Token de acceso | 15 minutos | No |
| Token de refresco | 30 días | Sí |

El token de refresco **rota en cada uso**: al canjearlo recibes uno nuevo y el anterior
queda consumido.

**Si un token ya consumido vuelve a usarse, se revoca toda la sesión.** Sólo hay una
explicación razonable para que eso ocurra —alguien lo copió— y ante la duda se cierra
todo. Si alguna vez te echa la sesión sin motivo aparente, esto es lo primero que hay que
mirar en los logs: aparece como `Reutilizacion de token de refresco del usuario ...`.

Para cerrar sesión en un dispositivo: `POST /api/v1/auth/logout` con el token de
refresco.

---

## 8. Base de datos

Los datos viven en el volumen Docker `infra_gastos-db-data` y **sobreviven a parar los
contenedores**.

```bash
docker compose -f infra/docker-compose.yml stop db
```

```bash
docker compose -f infra/docker-compose.yml down -v
```

El segundo **borra el volumen y con él todos los datos**, incluida tu cuenta. Después de
eso, `needsBootstrap` vuelve a ser `true`.

### Copia de seguridad

```bash
docker exec gastos-db pg_dump -U gastos gastos > copia.sql
```

Todavía no hay nada automatizado. Ver el apartado de pendientes.

---

## 9. Antes de exponerlo a internet

Ninguna de estas es cuestión de código: son de operación.

- [ ] **TLS obligatorio.** Sin HTTPS, el token de acceso viaja en claro y todo lo demás
      da igual. La aplicación no termina TLS por sí misma: depende de la plataforma.
- [ ] **`JWT_SECRET` aleatorio**, distinto del de desarrollo.
- [ ] **Contraseña de PostgreSQL** distinta de `gastos_local_dev`.
- [ ] **`CORS_ALLOWED_ORIGINS`** apuntando al dominio real del frontend, no a
      `localhost:5173`.
- [ ] **Correo configurado** (`MAIL_HOST` y compañía) y `APP_BASE_URL` con la URL
      pública. Sin ellos, el restablecimiento de contraseña no funciona.
- [ ] **`connect-src` de la CSP** en `frontend/_headers`, apuntando al dominio real de
      la API.
- [ ] **`WEBAUTHN_RP_ID`** con el dominio del frontend (no el de la API) y
      **`WEBAUTHN_ORIGINS`** con la URL completa en `https://`. Sin esto las passkeys no
      funcionan, y el navegador no explica por qué.
- [ ] **Copia de seguridad** de la base de datos con alguna periodicidad.

---

## 10. Pendiente, que conviene tener presente

Cosas que hoy **no** existen y que pueden morder:

- **No hay copia de seguridad automática.**
- **No hay pantalla para dar de alta al segundo conviviente**; hoy sólo por API.
- **No hay expulsión de sesiones** desde una pantalla: revocar exige llamar a `logout`
  con el token de refresco correspondiente, o borrar filas de `refresh_token`.
- **Rotación del `JWT_SECRET` sin cortar sesiones**: haría falta soportar dos claves a la
  vez durante la transición.
