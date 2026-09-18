# Gastos

Gestión financiera personal para un hogar de dos personas: resumen, gastos, cuentas
bancarias y herramientas de hipoteca con análisis de viabilidad para la Comunidad de
Madrid.

## Estado actual

Rama `feature/persistence-postgresql`: dominio, motor de cálculo hipotecario con
programas de ayuda configurables, **API REST completa** (gastos, cuentas e hipoteca) y
**persistencia en PostgreSQL** con migraciones Flyway. 121 tests en verde, incluidos 9
de arquitectura.

Las migraciones y el mapeo están verificados contra **PostgreSQL 16 real**, no sólo
contra la H2 de los tests: esquema creado por Flyway, datos escritos por la API y
releídos intactos tras reiniciar el proceso.

Todavía **no hay autenticación**: la identidad viaja en una cabecera sin firmar, así que
la API no debe salir de la red local. El plan de ramas está en
[docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md).

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
| Frontend | PWA instalable, tema claro Verde Salvia |
| Despliegue | Fly.io + Neon + Cloudflare Pages ([ADR-0002](docs/adr/ADR-0002-plataforma-de-despliegue.md)) |

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
├── frontend/            PWA (tokens de diseño y manifiesto)
├── infra/               Dockerfile y docker-compose
└── docs/                arquitectura, ADR, flujo Git, seguridad
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

Los tests no necesitan Docker: usan H2 en modo de compatibilidad PostgreSQL con las
mismas migraciones de Flyway que se despliegan.

## Documentación

- [Arquitectura](docs/ARCHITECTURE.md)
- [Inventario de la API](docs/API.md)
- [ADR-0001 — Monolito modular frente a microservicios](docs/adr/ADR-0001-monolito-modular-vs-microservicios.md)
- [ADR-0002 — Plataforma de despliegue gratuita](docs/adr/ADR-0002-plataforma-de-despliegue.md)
- [ADR-0003 — No almacenar datos bancarios identificativos](docs/adr/ADR-0003-no-almacenar-datos-bancarios.md)
- [ADR-0004 — Los programas de ayuda son datos, no código](docs/adr/ADR-0004-programas-de-ayuda-como-datos.md)
- [Flujo de trabajo Git](docs/GIT_WORKFLOW.md)
- [Seguridad y cumplimiento](docs/SECURITY.md)
- [OWASP API Security Top 10 (2023)](docs/OWASP-API-SECURITY.md)

## Aviso

Las cifras del programa *Mi Primera Vivienda* y los tipos impositivos incluidos son
parámetros de trabajo configurables, no asesoramiento financiero. Contrástalos con la
normativa vigente de la Comunidad de Madrid y con tu entidad antes de tomar decisiones.
