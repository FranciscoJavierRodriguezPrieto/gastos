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

- **JWT firmado (HS256) de 15 minutos** para el acceso, y token de refresco **opaco,
  rotatorio y revocable** de 30 días. Detalle y razones en
  [ADR-0005](adr/ADR-0005-autenticacion-con-jwt.md).
- **Reutilizar un token de refresco revoca toda la sesión**: un token ya consumido que
  reaparece sólo se explica porque alguien lo copió.
- **Contraseñas con BCrypt coste 12** y mínimo 12 caracteres. Sólo se guarda el hash, y
  en una tabla aparte de los usuarios.
- **Mismo error para todo fallo de login**, y se calcula un hash de descarte cuando el
  correo no existe: ni el cuerpo ni el tiempo de respuesta delatan qué cuentas hay.
- **Sin registro abierto**: el alta inicial sólo funciona mientras no exista ningún
  hogar; después, sólo el `OWNER` da de alta al otro conviviente.
- **La clave de firma no tiene valor por defecto**: sin `JWT_SECRET` la aplicación no
  arranca.
- **Restablecimiento por correo** con token opaco de un solo uso y 30 minutos de vida,
  guardado como hash. Pedirlo responde igual exista o no la cuenta; restablecer revoca
  todas las sesiones; pedir un enlace nuevo invalida el anterior.

**Estado: hecho.** `AuthApiTest` (11 tests: rotación, reutilización, cierre de sesión,
registro cerrado, credenciales indistinguibles), `PasswordResetApiTest` (11 tests del
flujo de recuperación), `MortgageApiTest.tamperedTokenIsRejected`.

---

### API3:2023 — Broken Object Property Level Authorization

Incluye los antiguos *excessive data exposure* y *mass assignment*.

- **Salida:** DTO de respuesta explícito por recurso, con lista fija de campos. Lo que
  no esté declarado no puede llegar al cliente por descuido al añadir una pantalla
  nueva: ni `AccountResponse` ni `ExpenseResponse` exponen `householdId`, y
  `ExpenseResponse` tampoco `registeredBy`.
- **Minimización en origen:** la forma más segura de no filtrar un dato es no
  guardarlo. El IBAN se eliminó del modelo por esa razón (ver
  [ADR-0003](adr/ADR-0003-no-almacenar-datos-bancarios.md)).
- **Entrada:** DTO de petición explícito y `fail-on-unknown-properties: true`. Un JSON
  que traiga `id` o `householdId` se rechaza con 400 en vez de ignorarse en silencio.
- Las operaciones sobre el saldo tienen **DTO propio** (`BalanceOperationRequest`), así
  que un apunte no puede colar de paso un cambio de titularidad o de alias.

**Estado: hecho.** `AccountRestMapperTest.responseExposesOnlyDeclaredFields`,
`AccountApiTest.openAccountDoesNotLeakHousehold`, `ExpenseApiTest.unknownFieldIsRejected`.

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
- La clave del limitador es **el sujeto del token**, no una cabecera: una cabecera la
  controla el cliente y bastaría rotarla para esquivar el límite. Por eso el filtro se
  registra *dentro* de la cadena de seguridad y después de autenticar.

**Estado: hecho.** `ApiHardeningTest.rateLimitReturns429`.
*Limitación conocida:* el contador es en memoria y por instancia. Con más de un nodo
haría falta un contador compartido.

---

### API5:2023 — Broken Function Level Authorization

- Criterio de la cadena de seguridad: **todo exige token salvo una lista blanca corta y
  explícita**. Un endpoint nuevo queda protegido por omisión.
- Los dos usuarios del hogar tienen los mismos privilegios sobre los datos compartidos.
  El único privilegio diferenciado es `Role.OWNER`, que es quien puede dar de alta al
  otro conviviente, y se comprueba en el caso de uso: un `MEMBER` recibe 403.

**Estado: hecho.** `AuthApiTest.onlyOwnerCanAddMembers`.

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
| API2 Autenticación | Hecho (Passkeys pendiente, ver abajo) |
| API3 Propiedades del objeto | Hecho |
| API4 Consumo de recursos | Hecho (contador por instancia) |
| API5 Autorización de función | Hecho |
| API6 Flujos de negocio | Hecho |
| API7 SSRF | No aplica |
| API8 Configuración | Hecho |
| API9 Inventario | Hecho |
| API10 Consumo de APIs | No aplica |

**Ya no queda ningún riesgo bloqueante para un despliegue público.** La identidad va
firmada y verificada, y el aislamiento por hogar deja de ser una convención.

Antes de exponer la instancia a internet quedan dos cosas de operación, no de código:
servir siempre bajo **TLS** (sin él, el token viaja en claro) y generar un `JWT_SECRET`
aleatorio y distinto del de desarrollo.

**Passkeys (WebAuthn) queda fuera a propósito**, como rama aparte: tiene su propio ciclo
de registro de credenciales y recuperación, y entregarlo a medias sería peor que no
entregarlo.
