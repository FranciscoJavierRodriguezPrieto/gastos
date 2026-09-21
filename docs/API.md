# Inventario de la API (v1)

Base: `/api/v1`. Todas las respuestas son JSON. Importes en euros con dos decimales;
porcentajes en unidades de tanto por ciento (`30.00` = 30%).

Mantener este inventario al día es parte del trabajo, no documentación opcional: un
endpoint olvidado y sin vigilar es exactamente OWASP API9.

## Autenticación

Todo exige `Authorization: Bearer <token de acceso>`, salvo los endpoints públicos que se
listan abajo. Sin token válido: **401**.

El hogar y el usuario se leen del token. No hay ninguna cabecera de identidad que el
cliente pueda escribir.

| Método | Ruta | Público | Descripción |
|---|---|---|---|
| `GET` | `/auth/status` | sí | Si la instalación necesita alta inicial. |
| `POST` | `/auth/register` | sí | Crea el hogar y su titular. **Sólo funciona si no existe ningún hogar.** Devuelve la sesión ya iniciada. |
| `POST` | `/auth/login` | sí | Inicia sesión. |
| `POST` | `/auth/refresh` | sí | Canjea el token de refresco por uno nuevo. **Rota**: el anterior queda consumido. |
| `POST` | `/auth/logout` | sí | Revoca el token de refresco. Siempre `204`. |
| `POST` | `/auth/forgot-password` | sí | Pide el enlace de restablecimiento. **Siempre `204`**, exista la cuenta o no. |
| `POST` | `/auth/reset-password` | sí | Fija la contraseña con el token del correo y devuelve la sesión iniciada. |
| `POST` | `/auth/password` | no | Cambia la contraseña. Exige la actual. `204`. |
| `GET` | `/auth/me` | no | El usuario autenticado. |
| `GET` | `/auth/members` | no | Miembros del hogar. |
| `POST` | `/auth/members` | no | Da de alta al segundo conviviente. **Sólo el `OWNER`**; un `MEMBER` recibe `403`. |

### Passkeys (WebAuthn)

| Método | Ruta | Público | Descripción |
|---|---|---|---|
| `POST` | `/auth/passkeys/registration/options` | no | Parámetros y reto para `navigator.credentials.create()`. |
| `POST` | `/auth/passkeys/registration` | no | Guarda la passkey si la respuesta verifica. `201`. |
| `POST` | `/auth/passkeys/authentication/options` | sí | Reto para `navigator.credentials.get()`. |
| `POST` | `/auth/passkeys/authentication` | sí | Entra con la passkey. Devuelve **los mismos tokens que `/auth/login`**. |
| `GET` | `/auth/passkeys` | no | Las passkeys propias. |
| `DELETE` | `/auth/passkeys/{id}` | no | Da de baja una passkey **propia**. Una ajena responde `404`. |

Todo lo binario viaja en **base64url sin relleno**, que es lo que produce y espera el
navegador.

El alta exige sesión iniciada; el acceso no puede exigirla, porque quien entra con passkey
todavía no tiene token. Un reto emitido para una ceremonia **no sirve para la otra**, y el
de alta va además atado a su usuario.

El reto se **consume antes de verificar**: un fallo no deja vivo un reto con el que
reintentar. Repetir una respuesta capturada devuelve `401`.

La respuesta del listado no incluye ni el identificador de la credencial ni la clave
pública: para reconocer y quitar un dispositivo bastan el nombre y las fechas (API3).

Razones del diseño en [ADR-0007](adr/ADR-0007-passkeys-con-webauthn4j.md).

**Token de acceso:** JWT firmado, 15 minutos, no revocable.
**Token de refresco:** cadena opaca, 30 días, revocable. Se entrega una sola vez y en base
de datos sólo queda su hash.

**Reutilizar un token de refresco ya canjeado revoca toda la sesión.** Es la defensa ante
un token robado: si reaparece uno consumido, caen todos.

**Restablecer o cambiar la contraseña revoca todas las sesiones.** El token del enlace
dura 30 minutos, sirve una sola vez, y pedir uno nuevo invalida el anterior.

**La passkey no sustituye a la contraseña.** Convive con ella: es el camino rápido del día
a día, y la contraseña se queda como vía de recuperación si se pierde el dispositivo.

Las razones de este diseño están en [ADR-0005](adr/ADR-0005-autenticacion-con-jwt.md).

## Gastos

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/expenses` | Registra un gasto. `201` + `Location`. |
| `GET` | `/expenses?month=YYYY-MM` | Gastos del mes, del más reciente al más antiguo. |
| `GET` | `/expenses/summary?month=YYYY-MM` | Total, compromisos mensuales y desglose por categoría con su peso. |
| `GET` | `/expenses/{id}` | Un gasto. |
| `PUT` | `/expenses/{id}` | Modifica descripción, importe, categoría, periodicidad y fecha. |
| `DELETE` | `/expenses/{id}` | Borra. `204`. |
| `GET` | `/expenses/catalog` | Categorías y periodicidades admitidas, para que el frontend no mantenga una copia. |

`monthlyCommitments` del resumen es lo que alimenta `otherMonthlyDebts` del simulador.

## Cuentas

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/accounts` | Alta de cuenta. `201` + `Location`. |
| `GET` | `/accounts` | Cuentas del hogar, por alias. |
| `GET` | `/accounts/total-balance` | Patrimonio agregado del hogar. |
| `GET` | `/accounts/{id}` | Una cuenta. |
| `PUT` | `/accounts/{id}/alias` | Renombra. |
| `POST` | `/accounts/{id}/credit` | Ingreso. |
| `POST` | `/accounts/{id}/debit` | Cargo. Rechazado si deja descubierto (salvo tarjeta de crédito). |
| `PUT` | `/accounts/{id}/balance` | Concilia contra el extracto. |
| `DELETE` | `/accounts/{id}` | Cierra. `204`. |

No se pide el IBAN: los saldos se introducen a mano y no hay integración bancaria, así
que el alias y el nombre del banco identifican la cuenta de sobra.

## Hipoteca

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/mortgage/simulations` | Simulación efímera. No persiste nada. Es la que mueven los deslizadores. |
| `GET` | `/mortgage/programs` | Programas de ayuda del hogar. |
| `POST` | `/mortgage/programs` | Alta de programa. `201` + `Location`. |
| `GET` | `/mortgage/programs/{id}` | Un programa. |
| `PUT` | `/mortgage/programs/{id}` | Modifica LTV, límites, requisitos y estado. |
| `DELETE` | `/mortgage/programs/{id}` | Borra. `204`. |
| `POST` | `/mortgage/programs/reference-catalog` | Instala el catálogo de partida. Idempotente. |
| `GET` | `/mortgage/scenarios` | Escenarios guardados, cada uno recalculado. |
| `POST` | `/mortgage/scenarios` | Guarda un escenario. `201` + `Location`. |
| `GET` | `/mortgage/scenarios/{id}` | Entrada guardada + resultado recalculado con las políticas vigentes. |
| `PUT` | `/mortgage/scenarios/{id}` | Actualiza nombre y parámetros. |
| `DELETE` | `/mortgage/scenarios/{id}` | Borra. `204`. |

La simulación es `POST` aunque no modifique nada: son una docena de parámetros
económicos del hogar y en la query string acabarían en los registros de acceso y en el
historial del navegador.

### Modos de financiación

`financingMode` decide de dónde sale el LTV máximo. Si se omite se asume `AUTOMATICO`.

| Modo | Campos extra | Comportamiento |
|---|---|---|
| `AUTOMATICO` | — | Aplica el programa activo que más financie de entre los que se cumplan. Sin programas, el 80% estándar. |
| `PROGRAMA` | `programId` | Aplica ese programa. Si no se cumple, explica qué falta y aplica el 80%. |
| `MANUAL` | `manualLoanToValue` | Usa ese LTV sin comprobar ningún requisito. |

**El cálculo de cuota, DTI y veredicto es idéntico en los tres modos.** Un programa sólo
decide cuánto presta el banco.

La respuesta incluye `financingDecision` con el LTV aplicado, el programa usado (si
alguno), las notas que lo explican y el estado de **cada** programa del catálogo frente
a ese escenario, con los requisitos concretos que incumple.

En los programas, `maxPropertyPrice` y `maxApplicantAge` ausentes significan "sin
límite".

### Situación del hogar y fiscalidad

La simulación admite tres campos opcionales, todos pensados para la normativa de Madrid
([ADR-0008](adr/ADR-0008-normativa-madrid-2026.md)):

| Campo | Por defecto | Qué cambia |
|---|---|---|
| `familyWithChildren` | `false` | Familia con hijos menores, numerosa o monoparental: da acceso a Mi Primera Vivienda al 100% sin límite de edad. |
| `largeFamily` | `false` | Título de familia numerosa: ITP al 4%. |
| `primaryResidence` | `true` | Vivienda habitual: condición de la rebaja del ITP y de los programas de primera vivienda. |

`applicantAge` es la edad del **mayor** de los compradores: los programas exigen que todos
cumplan el límite.

`upfrontCosts` devuelve además `transferTaxRate` (el tipo de ITP aplicado) y
`transferTaxBasis` (el motivo, en palabras). Los programas llevan `requiresFamily`.

## Operación

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/actuator/health` | Sonda de vida. Sin detalle. Fuera de `/api/v1`. |

## Errores

Formato único:

```json
{
  "timestamp": "2026-09-18T10:15:30Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "La peticion contiene campos no validos",
  "path": "/api/v1/expenses",
  "details": ["amount: El importe debe ser mayor que cero"]
}
```

| Código | Cuándo |
|---|---|
| `401` | Falta el token, no es válido, ha caducado, o las credenciales no cuadran. |
| `403` | El token es válido pero el rol no alcanza. |
| `400` | Petición mal formada: falta un campo, el tipo no encaja, falta una cabecera, hay un campo desconocido. |
| `404` | El recurso no existe **o no es de este hogar**. Los dos casos responden igual a propósito. |
| `422` | La petición es válida pero una regla de negocio la rechaza (cuenta conjunta con un solo titular, descubierto, categoría inexistente). |
| `429` | Límite de peticiones superado. Incluye `Retry-After`. |
| `500` | Error inesperado. El detalle se queda en el log del servidor. |

## Ejemplo

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"householdName":"Nuestra casa","email":"titular@ejemplo.es","displayName":"Titular","password":"una-contrasena-larga-y-decente","monthlyNetIncome":2200.00}'
```

Después, con el `accessToken` que devuelve:

```bash
curl -X POST http://localhost:8080/api/v1/mortgage/simulations \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <accessToken>' \
  -d '{"propertyPrice":280000,"availableSavings":50000,"targetReserve":5000,"annualNominalRate":3.00,"termYears":30,"netMonthlyIncome":4500,"otherMonthlyDebts":225,"applicantAge":32,"firstHome":true,"financingMode":"AUTOMATICO"}'
```

Instalar el catálogo de partida:

```bash
curl -X POST http://localhost:8080/api/v1/mortgage/programs/reference-catalog \
  -H 'Authorization: Bearer <accessToken>'
```
