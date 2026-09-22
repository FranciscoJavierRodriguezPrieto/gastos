# Flujo de trabajo Git

## Ramas permanentes

| Rama | Propósito | Reglas |
|---|---|---|
| `main` | Producción. Estable, desplegable y probado. | Sólo recibe merges desde `develop` (o desde `hotfix/*`). Cada merge se etiqueta. |
| `develop` | Integración del desarrollo activo. | Sólo recibe merges desde ramas de trabajo con CI en verde. |

## Ramas de trabajo

Se crean **siempre desde `develop`** y se integran **siempre en `develop`**.

| Prefijo | Uso | Ejemplo |
|---|---|---|
| `feature/` | Módulo o funcionalidad nueva | `feature/mortgage-rest-api` |
| `fix/` | Corrección de un fallo | `fix/dti-redondeo-cuota` |
| `refactor/` | Mejora de código o arquitectura, sin cambio funcional | `refactor/extraer-politica-itp` |
| `docs/` | Sólo documentación | `docs/adr-despliegue` |
| `chore/` | Build, dependencias, CI | `chore/actualizar-spring-boot` |
| `hotfix/` | Urgencia sobre producción; sale de `main` y entra en `main` **y** `develop` | `hotfix/error-500-simulacion` |

## Ciclo de una tarea

```bash
git checkout develop && git pull --ff-only
git checkout -b feature/<nombre>
# ... trabajo, con commits pequeños y verificables ...
mvn clean verify
git push -u origin feature/<nombre>
# Pull Request hacia develop
```

Condiciones para integrar en `develop`:

1. `mvn clean verify` en verde, incluidos los tests de arquitectura.
2. Sin reducción de cobertura en la lógica de negocio.
3. Merge con `--no-ff`, para que la historia conserve el contorno de la funcionalidad.

Paso a producción:

```bash
git checkout main && git merge --no-ff develop
git tag -a v0.1.0 -m "Primera versión desplegable"
```

## Convenio de commits

Se usa [Conventional Commits](https://www.conventionalcommits.org/), en español y en
imperativo:

```
<tipo>(<ámbito>): <qué cambia y por qué>

feat(mortgage): calcular DTI total incluyendo deudas previas
fix(expenses): corregir prorrateo de gastos anuales
refactor(shared): extraer Guard para invariantes de dominio
test(mortgage): cubrir escenarios de viabilidad ajustada
docs(adr): registrar la decisión de monolito modular
chore(build): fijar Java 21 como release del compilador
```

**Ámbitos:** `shared`, `iam`, `accounts`, `expenses`, `mortgage`, `bootstrap`,
`frontend`, `infra`, `build`, `adr`.

Un commit = un cambio con sentido propio. Nada de `wip` ni `varios arreglos`.

## Plan de ramas del proyecto

| Orden | Rama | Estado | Contenido |
|---|---|---|---|
| 1 | `feature/architecture-setup` | Integrada | Estructura, modelo de dominio, motor de hipoteca, tests, ADR. |
| 2 | `feature/rest-api` | Integrada | API REST completa, DTOs, mappers, CRUD, manejo de errores, bastionado OWASP API. |
| 3 | `refactor/quitar-iban` | Integrada | Se elimina el IBAN: dato personal sin función en el producto (ADR-0003). |
| 4 | `refactor/mortgage-programas-configurables` | Integrada | Programas de ayuda como datos + modos de financiación (automático / programa / manual). |
| 5 | `feature/persistence-postgresql` | Integrada | Adaptadores JPA, Flyway y Docker Compose. Sustituye a los repositorios en memoria. |
| 6 | `feature/security-jwt` | Integrada | Spring Security, JWT con refresco rotatorio, usuarios en base de datos. Cierra OWASP API2. |
| 7 | `feature/pwa-shell` | Integrada | PWA instalable: acceso, sesión, navegación, service worker, y las pantallas de Resumen, Gastos y Cuentas. |
| 8 | `feature/mortgage-ui` | Integrada | Herramienta de hipoteca: deslizadores, catálogo de programas y veredicto. |
| 9 | `feature/password-reset` | **Actual** | Restablecimiento por correo con Brevo, cambio de contraseña y pantalla de cuenta. |
| 10 | `feature/passkeys` | Hecho | WebAuthn como alternativa a la contraseña. Encajó encima de JWT sin rehacer nada, como se preveía. |
| 11 | `chore/deployment-pipeline` | Hecho | CI con imagen y revisión de dependencias, Dependabot, digests fijados, `fly.toml`, script de preparación del frontend y [manual de despliegue](DESPLIEGUE.md). Falta ejecutarlo de verdad contra las cuentas. |
| 13 | `feature/gastos-fijos` | **Actual** | Gastos fijos como plantilla que se expande al abrir el mes. Corregir un mes no toca los demás y subir el importe no reescribe lo ya pagado. |
| 12 | `feature/invitacion-pareja` | Hecho | El titular invita con un código de un solo uso y la pareja se da de alta ella misma eligiendo su contraseña. Sustituye a `POST /auth/members`, donde el titular elegía la contraseña del otro. Incluye cerrar el ADR-0002: la API va a **Render** (`render.yaml`), con la base de datos en Neon porque la PostgreSQL gratuita de Render caduca a los 30 días. |
