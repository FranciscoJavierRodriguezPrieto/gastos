# OWASP API Security Top 10 (2023)

Edición de referencia: **OWASP API Security Top 10 – 2023**, la última publicada que
conozco. Antes de dar por cerrado este documento conviene comprobar en
[owasp.org/API-Security](https://owasp.org/API-Security/) si ha salido una edición
posterior; si es así, hay que revisar este mapeo.

Estado por riesgo. `Hecho` significa implementado **y cubierto por un test** que falla
si la protección desaparece.

---

### API1:2023 — Broken Object Level Authorization (BOLA / IDOR)

El riesgo número uno: pedir el recurso de otro cambiando un identificador.

- Identificadores **UUID v4 opacos**, nunca enteros autoincrementales.
- `HouseholdId` es **obligatorio en la firma de todos los puertos**: no existe un
  `findById(id)` que se pueda llamar sin acotar por hogar.
- **Doble barrera**: el repositorio filtra por hogar *y* el caso de uso vuelve a
  comprobar `isAccessibleBy()` sobre el agregado recuperado.
- Un recurso ajeno responde **404, nunca 403**: un 403 confirmaría que el
  identificador existe y permitiría enumerarlos.

**Estado: hecho.** `ExpenseApiTest.otherHouseholdGetsNotFound`,
`MortgageApiTest.otherHouseholdCannotReadScenario`.

---

### API2:2023 — Broken Authentication

- **Pendiente.** Hoy la identidad llega por las cabeceras `X-Household-Id` y
  `X-User-Id`, que es un andamio de desarrollo: **cualquiera que invente una cabecera
  es cualquier hogar**.
- Comprometido para `feature/security-jwt-passkeys`: JWT de vida corta con refresh
  rotatorio o Passkeys (WebAuthn), sin registro abierto (alta por invitación del
  `OWNER`).

**Estado: pendiente. La API no debe salir de la red local hasta cerrarlo.**

---

### API3:2023 — Broken Object Property Level Authorization

Incluye los antiguos *excessive data exposure* y *mass assignment*.

- **Salida:** DTO de respuesta explícito por recurso. `AccountResponse` sólo tiene
  `maskedIban`; **no existe un campo con el IBAN completo**, así que no hay forma de
  filtrarlo por descuido al añadir una pantalla. `ExpenseResponse` no expone
  `householdId` ni `registeredBy`.
- **Entrada:** DTO de petición explícito y `fail-on-unknown-properties: true`. Un JSON
  que traiga `id` o `householdId` se rechaza con 400 en vez de ignorarse en silencio.
- Las operaciones sobre el saldo tienen **DTO propio** (`BalanceOperationRequest`), así
  que un apunte no puede colar de paso un cambio de IBAN o de titularidad.

**Estado: hecho.** `AccountRestMapperTest.responseNeverExposesFullIban`,
`AccountApiTest.openAccountMasksIban`, `ExpenseApiTest.unknownFieldIsRejected`.

---

### API4:2023 — Unrestricted Resource Consumption

El simulador se invoca en cada movimiento de un deslizador: un bucle mal escrito satura
el proceso sin mala intención, y en una instancia gratuita de 512 MB eso es una caída.

- **Limitador de peticiones** por ventana fija (`RateLimitFilter`), configurable, con
  respuesta 429 y cabecera `Retry-After`.
- **Límites de tamaño**: cabeceras 8 KB, cuerpo 256 KB, `connection-timeout` 10 s, tope
  de 50 hilos.
- **Rangos de cordura en los DTO** (precio máximo, plazo 5–40 años, tipo ≤ 25%): una
  petición absurda se rechaza antes de consumir CPU.
- La clave del limitador **no confía en `X-Forwarded-For`**, que el cliente controla y
  podría rotar para esquivar el límite.

**Estado: hecho.** `ApiHardeningTest.rateLimitReturns429`.
*Limitación conocida:* el contador es en memoria y por instancia. Con más de un nodo
haría falta un contador compartido.

---

### API5:2023 — Broken Function Level Authorization

- La API no tiene operaciones de administración: los dos usuarios del hogar tienen los
  mismos privilegios sobre los datos compartidos.
- El único privilegio diferenciado es `Role.OWNER` (invitar y revocar al otro miembro),
  hoy sólo modelado en el dominio.
- **Pendiente:** la comprobación a nivel HTTP llega con Spring Security en
  `feature/security-jwt-passkeys`.

**Estado: parcial.**

---

### API6:2023 — Unrestricted Access to Sensitive Business Flows

- No hay flujo con valor de reventa (ni compras, ni invitaciones masivas, ni registro
  abierto): la superficie de abuso es mínima por diseño.
- El alta de miembros está topada en el agregado (`Household.MAX_MEMBERS = 2`), no en
  el formulario.
- El limitador de API4 cubre el abuso por volumen.

**Estado: hecho** en lo que aplica a este producto.

---

### API7:2023 — Server Side Request Forgery

La aplicación **no hace ninguna petición saliente** y no acepta URLs del usuario. Si
algún día se añade importación de movimientos desde una URL, habrá que revisarlo.

**Estado: no aplica hoy.**

---

### API8:2023 — Security Misconfiguration

- **Cabeceras** en todas las respuestas: `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, CSP `default-src 'none'`, `Referrer-Policy: no-referrer`,
  `Cache-Control: no-store` y HSTS cuando hay TLS.
- **CORS explícito y restrictivo**: lista blanca de orígenes, sin comodín y sin
  credenciales. Vacío significa que no se admite ninguno.
- **Errores que no filtran nada**: formato único `ApiError`, sin trazas ni nombres de
  clase. El detalle de Jackson (que puede incluir fragmentos del cuerpo enviado) se
  registra pero no se devuelve.
- Cabecera `Server` suprimida; sin endpoints de depuración.

**Estado: hecho.** `ApiHardeningTest.securityHeadersArePresent`,
`ApiHardeningTest.unknownRouteLeaksNothing`.

---

### API9:2023 — Improper Inventory Management

- **Versionado en la ruta** desde el primer día: `/api/v1/...`.
- **Inventario escrito** de todos los endpoints en [API.md](API.md).
- **Superficie de gestión mínima**: Actuator expone sólo `health`, y sin detalle.
- Los adaptadores provisionales (repositorios en memoria) están marcados como tales en
  su Javadoc, con la rama que los sustituirá.

**Estado: hecho.** `ApiHardeningTest.actuatorSurfaceIsMinimal`.

---

### API10:2023 — Unsafe Consumption of APIs

No se consume ninguna API de terceros: ni pasarelas bancarias, ni analítica, ni fuentes
de tipos de interés. Los tipos los introduce el usuario.

**Estado: no aplica hoy.** Si se integra agregación bancaria (PSD2), habrá que validar
sus respuestas con el mismo rigor que las del usuario.

---

## Resumen

| Riesgo | Estado |
|---|---|
| API1 BOLA | Hecho |
| API2 Autenticación | **Pendiente** (`feature/security-jwt-passkeys`) |
| API3 Propiedades del objeto | Hecho |
| API4 Consumo de recursos | Hecho (contador por instancia) |
| API5 Autorización de función | Parcial (dominio sí, HTTP pendiente) |
| API6 Flujos de negocio | Hecho |
| API7 SSRF | No aplica |
| API8 Configuración | Hecho |
| API9 Inventario | Hecho |
| API10 Consumo de APIs | No aplica |

**Lo único que bloquea un despliegue público es API2.** Mientras la identidad viaje en
una cabecera sin firmar, todo lo demás es irrelevante: basta con inventarse un
`X-Household-Id` para ser otro hogar.
