# Seguridad y cumplimiento

Documento vivo. Marca lo que ya está implementado y lo que queda comprometido para
ramas posteriores, para que la deuda de seguridad sea visible en vez de implícita.

> El **OWASP API Security Top 10 (2023)**, que es el catálogo específico de APIs y el
> que más aplica a este proyecto, tiene su propio documento con el estado riesgo por
> riesgo: [OWASP-API-SECURITY.md](OWASP-API-SECURITY.md).

## OWASP Top 10 — mitigaciones

| Riesgo | Mitigación | Estado |
|---|---|---|
| **A01 Control de acceso roto (BOLA/IDOR)** | Identificadores UUID no enumerables; `HouseholdId` obligatorio en la firma de todos los puertos; doble comprobación en el agregado (`isAccessibleBy`) además de en la consulta. | Implementado en el dominio |
| **A02 Fallos criptográficos** | TLS obligatorio en tránsito; cifrado en reposo del proveedor. No se almacena ningún dato bancario identificativo, así que no hay nada que cifrar a nivel de columna. De las passkeys sólo se guarda la **clave pública**: no hay secreto que robar. | Hecho por diseño ([ADR-0003](adr/ADR-0003-no-almacenar-datos-bancarios.md)) |
| **A03 Inyección** | Sin SQL concatenado: Spring Data JPA con consultas derivadas y parámetros ligados; validación de invariantes en el constructor de cada value object (`Guard`, `Email`, `Money`). | Validación de dominio hecha |
| **A04 Diseño inseguro** | Límite de dos miembros por hogar impuesto en el agregado; importes de gasto siempre positivos; descubierto prohibido salvo en tarjetas de crédito. | Implementado |
| **A05 Configuración insegura** | Actuator reducido a `health` sin detalle; cabecera `Server` suprimida; mensajes y trazas de error nunca se devuelven al cliente. | Implementado en `application.yml` |
| **A06 Componentes vulnerables** | Dependabot semanal sobre Maven, acciones de GitHub e imágenes Docker; `dependency-review-action` bloquea los PR que introducen una vulnerabilidad alta o crítica; BOM de Spring Boot para versiones coherentes. | Hecho |
| **A07 Fallos de identificación y autenticación** | JWT de 15 min con refresh rotatorio y detección de reutilización; BCrypt coste 12; sin registro abierto; **passkeys (WebAuthn)** con verificación de usuario obligatoria y retos de un solo uso. | Hecho ([ADR-0005](adr/ADR-0005-autenticacion-con-jwt.md), [ADR-0007](adr/ADR-0007-passkeys-con-webauthn4j.md)) |
| **A08 Fallos de integridad** | Dependencias con versión fijada; imágenes base de Docker fijadas **por digest** en el `Dockerfile` y en el compose; acciones de GitHub por versión mayor, actualizadas por Dependabot; CI que compila, prueba y construye la imagen. | Hecho |
| **A09 Fallos de registro y monitorización** | `DomainException` con mensajes de negocio, sin datos personales en las trazas; logs estructurados con identificador de correlación. | Parcial |
| **A10 SSRF** | La única petición saliente es al relé SMTP, cuya dirección viene de la configuración y nunca del usuario. | No aplica |

Frente a **XSS** el frontend escapa por defecto y se sirve con una CSP restrictiva;
frente a **CSRF**, la API es *stateless* con token en cabecera `Authorization` (no en
cookie), lo que elimina el vector; si en algún momento se usaran cookies, serían
`SameSite=Strict` con token anti-CSRF.

## CCN-STIC-812 — criterios aplicados

- **Mínima superficie expuesta:** un solo puerto, Actuator reducido a `health`, cabecera
  `Server` suprimida, sin endpoints de depuración en producción.
- **Gestión de errores:** el usuario recibe un mensaje genérico; el detalle queda en el
  log del servidor. `include-stacktrace: never`.
- **Trazabilidad:** registro de autenticaciones y de operaciones que modifican saldos.
- **Cifrado:** TLS 1.2+ en tránsito; datos sensibles cifrados en reposo.
- **Privilegio mínimo:** el contenedor corre con usuario no root; el usuario de base de
  datos no tiene permisos DDL en producción (las migraciones usan credencial aparte).
- **Configuración fuera del código:** secretos por variable de entorno, nunca en el
  repositorio (ver `.gitignore`).

## Privacidad (RGPD)

- **Minimización:** sólo se almacena lo necesario. No se guarda ningún dato bancario
  identificativo: la aplicación funciona con alias, banco y saldo introducido a mano.
- **Sin terceros:** ni analítica, ni trazas externas, ni fuentes remotas. La PWA se
  sirve con todo el contenido propio.
- **Portabilidad y borrado:** exportación completa en JSON y borrado en cascada del
  hogar, comprometidos para la rama de persistencia.

## Pendiente explícito

Lo que **no** está hecho todavía, para que la deuda sea visible:

- **Pantalla de alta del segundo conviviente**; hoy sólo por API.
- **TLS**: la aplicación no termina TLS por sí misma; depende de la plataforma de
  despliegue. `fly.toml` fuerza HTTPS y Cloudflare Pages lo da de serie, pero mientras
  no haya un despliegue real esto está preparado, no verificado. Sin TLS, el token de
  acceso viaja en claro y todo lo demás da igual.
- **Copia de seguridad automática de la base de datos.** Hoy es un `pg_dump` manual.
- **Análisis de vulnerabilidades del inventario completo.** Se usa Dependabot y no
  OWASP Dependency-Check: éste necesita una clave de la NVD y, sin ella, una ejecución
  tarda horas y acaba desactivándose. Un análisis que nadie ejecuta no protege de nada.
- **Rotación del `JWT_SECRET`**: hoy cambiarlo invalida todas las sesiones de golpe.
  Aceptable para dos usuarios, pero conviene saberlo.
