# Inventario de la API (v1)

Base: `/api/v1`. Todas las respuestas son JSON. Importes en euros con dos decimales;
porcentajes en unidades de tanto por ciento (`30.00` = 30%).

Mantener este inventario al día es parte del trabajo, no documentación opcional: un
endpoint olvidado y sin vigilar es exactamente OWASP API9.

## Identidad (provisional)

| Cabecera | Uso |
|---|---|
| `X-Household-Id` | UUID del hogar. Obligatoria salvo en `/mortgage/simulations`. |
| `X-User-Id` | UUID del usuario. Sólo al registrar un gasto. |

> **Andamio de desarrollo.** Estas cabeceras desaparecen en
> `feature/security-jwt-passkeys`, donde el hogar y el usuario se leen del token.
> Hasta entonces la API no debe exponerse fuera de la red local.

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
historial del navegador. Exige `X-Household-Id` porque el catálogo de programas se
configura por hogar.

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
| `400` | Petición mal formada: falta un campo, el tipo no encaja, falta una cabecera, hay un campo desconocido. |
| `404` | El recurso no existe **o no es de este hogar**. Los dos casos responden igual a propósito. |
| `422` | La petición es válida pero una regla de negocio la rechaza (cuenta conjunta con un solo titular, descubierto, categoría inexistente). |
| `429` | Límite de peticiones superado. Incluye `Retry-After`. |
| `500` | Error inesperado. El detalle se queda en el log del servidor. |

## Ejemplo

```bash
curl -X POST http://localhost:8080/api/v1/mortgage/simulations \
  -H 'Content-Type: application/json' \
  -H 'X-Household-Id: 11111111-1111-1111-1111-111111111111' \
  -d '{"propertyPrice":280000,"availableSavings":50000,"targetReserve":5000,"annualNominalRate":3.00,"termYears":30,"netMonthlyIncome":4500,"otherMonthlyDebts":225,"applicantAge":32,"firstHome":true,"financingMode":"AUTOMATICO"}'
```

Instalar el catálogo de partida y volver a simular:

```bash
curl -X POST http://localhost:8080/api/v1/mortgage/programs/reference-catalog \
  -H 'X-Household-Id: 11111111-1111-1111-1111-111111111111'
```
