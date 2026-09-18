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

## 3. Contraseñas: entre 12 y 128 caracteres

**No se exigen mayúsculas, dígitos ni símbolos**, y es deliberado. Las reglas de
composición empujan a la gente hacia patrones predecibles del tipo `Verano2026!`, que un
atacante prueba de los primeros. La longitud es lo que de verdad encarece un ataque por
fuerza bruta.

También hay un **máximo de 128**: sin él, una contraseña enorme convertiría cada intento
de login en trabajo de CPU gratis para quien ataca.

Se guardan con **BCrypt de coste 12**, en una tabla aparte de los usuarios, y nunca se
devuelven por la API.

---

## 4. Poner en marcha desde cero

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

## 5. Sesiones

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

## 6. Base de datos

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

## 7. Antes de exponerlo a internet

Ninguna de estas es cuestión de código: son de operación.

- [ ] **TLS obligatorio.** Sin HTTPS, el token de acceso viaja en claro y todo lo demás
      da igual. La aplicación no termina TLS por sí misma: depende de la plataforma.
- [ ] **`JWT_SECRET` aleatorio**, distinto del de desarrollo.
- [ ] **Contraseña de PostgreSQL** distinta de `gastos_local_dev`.
- [ ] **`CORS_ALLOWED_ORIGINS`** apuntando al dominio real del frontend, no a
      `localhost:5173`.
- [ ] **Copia de seguridad** de la base de datos con alguna periodicidad.

---

## 8. Pendiente, que conviene tener presente

Cosas que hoy **no** existen y que pueden morder:

- **No hay recuperación de contraseña.** Si la olvidas, la única salida es entrar en la
  base de datos y sustituir el hash a mano. Con dos usuarios es asumible, pero es una
  carencia real, no un olvido.
- **No hay cambio de contraseña** desde la aplicación.
- **No hay copia de seguridad automática.**
- **No hay expulsión de sesiones** desde una pantalla: revocar exige llamar a `logout`
  con el token de refresco correspondiente, o borrar filas de `refresh_token`.
- **Rotación del `JWT_SECRET` sin cortar sesiones**: haría falta soportar dos claves a la
  vez durante la transición.
