# Gastos

Gestión financiera personal para un hogar de dos personas: resumen, gastos, cuentas
bancarias y herramientas de hipoteca con análisis de viabilidad para la Comunidad de
Madrid.

## Estado actual

Aplicación funcionalmente completa: backend, **PWA instalable con las cuatro pantallas**
—incluida la herramienta de hipoteca con deslizadores y catálogo de programas editable—,
**restablecimiento de contraseña por correo**, **acceso con passkey (WebAuthn)** e
**invitación de la pareja con código de un solo uso**.
**224 tests de backend y 31 de frontend y scripts.**

Verificado contra **PostgreSQL 16 real** y probado en el navegador, no sólo con tests.

**Todavía no está desplegado.** Los ficheros y el procedimiento están listos
([DESPLIEGUE.md](docs/DESPLIEGUE.md)); falta ejecutarlo contra cuentas reales de Neon,
Koyeb y Cloudflare. El destino cambió el 21/09/2026: **Render queda descartado** porque
sus horas gratuitas son por workspace y agotarlas suspende *todos* los servicios
gratuitos de la cuenta ([ADR-0002](docs/adr/ADR-0002-plataforma-de-despliegue.md)).

El plan de ramas está en [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md).

## Módulos funcionales

1. **Resumen** — balance total, ingresos del hogar, KPI de superávit y distribución del gasto.
2. **Gastos** — registro, categorización y prorrateo de gastos recurrentes.
3. **Mis Cuentas** — cuentas individuales y conjuntas, saldos y conciliación.
4. **Herramientas de Hipoteca** — calculadora y análisis de viabilidad:
   - catálogo de programas de ayuda **editable desde la aplicación**, con tres modos de
     financiación: automático, programa concreto o LTV manual;
   - gastos iniciales no financiables (ITP 6% + 4% de notaría, registro y gestoría);
   - DTI vivienda (límite 30%) y DTI total con deudas previas (límite 40%);
   - motor de simulación con barridos de ingresos, tipo de interés y precio.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Maven multi-módulo |
| Arquitectura | Monolito modular + hexagonal ([ADR-0001](docs/adr/ADR-0001-monolito-modular-vs-microservicios.md)) |
| Tests | JUnit 5, AssertJ, Mockito, ArchUnit |
| Base de datos | PostgreSQL (Neon en producción) |
| Seguridad | Spring Security, JWT HS256, BCrypt ([ADR-0005](docs/adr/ADR-0005-autenticacion-con-jwt.md)) |
| Frontend | PWA sin framework ni compilación ([ADR-0006](docs/adr/ADR-0006-frontend-sin-framework.md)) |
| Passkeys | WebAuthn4J tras un puerto ([ADR-0007](docs/adr/ADR-0007-passkeys-con-webauthn4j.md)) |
| Despliegue | Koyeb + Neon + Cloudflare Pages, coste 0 € ([ADR-0002](docs/adr/ADR-0002-plataforma-de-despliegue.md)) |

## Estructura

```
gastos/
├── backend/
│   ├── shared-kernel/   Money, Percentage, identificadores, invariantes
│   ├── iam/             usuarios, hogar, roles
│   ├── accounts/        cuentas bancarias y saldos
│   ├── expenses/        gastos, categorías, resumen mensual
│   ├── mortgage/        amortización, gastos de compraventa, DTI, viabilidad
│   └── bootstrap/       raíz de composición y artefacto ejecutable
├── frontend/            PWA: pantallas, cliente de la API y service worker
├── infra/               Dockerfile y docker-compose
├── scripts/             preparación del frontend para desplegar
├── fly.toml             alternativa de pago: la API en Fly.io
└── docs/                arquitectura, ADR, flujo Git, seguridad, despliegue
```

## Arrancar en local

Todo en Docker (API + base de datos):

```bash
docker compose -f infra/docker-compose.yml up --build
```

Solo la base de datos, y la API desde el JAR o el IDE:

```bash
docker compose -f infra/docker-compose.yml up -d db
```

```bash
mvn clean verify
```

```bash
java -jar backend/bootstrap/target/gastos.jar
```

**La aplicación no arranca sin `JWT_SECRET`**, y es intencionado: una clave de firma
por defecto en el código es una clave pública. Genera una y expórtala (esto y el resto de
la puesta en marcha, en el [manual de operación](docs/OPERACION.md)):

```bash
export JWT_SECRET=$(openssl rand -base64 48)
```

Con todo levantado, la aplicación queda en <http://localhost:5173>.

Los tests no necesitan Docker ni esa variable: usan H2 en modo de compatibilidad
PostgreSQL con las mismas migraciones de Flyway que se despliegan, y una clave fija de
pruebas.

```bash
node --test "frontend/test/*.test.js"
```

## Documentación

- [Manual de operación](docs/OPERACION.md) — puesta en marcha, secretos, cuentas y sesiones
- [Arquitectura](docs/ARCHITECTURE.md)
- [Inventario de la API](docs/API.md)
- [ADR-0001 — Monolito modular frente a microservicios](docs/adr/ADR-0001-monolito-modular-vs-microservicios.md)
- [ADR-0002 — Plataforma de despliegue gratuita](docs/adr/ADR-0002-plataforma-de-despliegue.md)
- [ADR-0003 — No almacenar datos bancarios identificativos](docs/adr/ADR-0003-no-almacenar-datos-bancarios.md)
- [ADR-0004 — Los programas de ayuda son datos, no código](docs/adr/ADR-0004-programas-de-ayuda-como-datos.md)
- [ADR-0005 — Autenticación con JWT y refresco rotatorio](docs/adr/ADR-0005-autenticacion-con-jwt.md)
- [ADR-0006 — Frontend sin framework ni compilación](docs/adr/ADR-0006-frontend-sin-framework.md)
- [Flujo de trabajo Git](docs/GIT_WORKFLOW.md)
- [Seguridad y cumplimiento](docs/SECURITY.md)
- [OWASP API Security Top 10 (2023)](docs/OWASP-API-SECURITY.md)

## Aviso

Las cifras del programa *Mi Primera Vivienda* y los tipos impositivos incluidos son
parámetros de trabajo configurables, no asesoramiento financiero. Contrástalos con la
normativa vigente de la Comunidad de Madrid y con tu entidad antes de tomar decisiones.
