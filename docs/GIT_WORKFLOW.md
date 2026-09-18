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

| Orden | Rama | Contenido |
|---|---|---|
| 1 | `feature/architecture-setup` | **Actual.** Estructura, modelo de dominio, motor de hipoteca, tests, ADR. |
| 2 | `feature/persistence-postgresql` | Adaptadores JPA, Flyway, cifrado de columnas sensibles. |
| 3 | `feature/rest-api-mortgage` | Endpoints REST del simulador, DTOs, validación, manejo de errores. |
| 4 | `feature/security-jwt-passkeys` | Spring Security, JWT/Passkeys, cabeceras, rate limiting. |
| 5 | `feature/pwa-shell` | PWA: manifest, service worker, tokens de diseño, instalable en iOS. |
| 6 | `feature/dashboard-ui` | Resumen, gráficos de distribución, KPI de superávit. |
| 7 | `feature/expenses-ui` y `feature/accounts-ui` | Pantallas de gastos y cuentas. |
| 8 | `feature/mortgage-ui` | Herramienta de hipoteca con simulación en tiempo real. |
| 9 | `chore/deployment-pipeline` | Docker, CI/CD, despliegue en Fly.io + Neon + Cloudflare Pages. |
