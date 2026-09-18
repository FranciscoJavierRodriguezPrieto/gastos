# Gastos

Gestión financiera personal para un hogar de dos personas: resumen, gastos, cuentas
bancarias y herramientas de hipoteca con análisis de viabilidad para la Comunidad de
Madrid.

## Estado actual

Rama `feature/architecture-setup`: estructura del proyecto, modelo de dominio en Java y
motor de cálculo hipotecario, con 48 tests en verde (incluidos 6 de arquitectura).
Todavía **no hay API REST, ni persistencia, ni autenticación**; el plan de ramas está en
[docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md).

## Módulos funcionales

1. **Resumen** — balance total, ingresos del hogar, KPI de superávit y distribución del gasto.
2. **Gastos** — registro, categorización y prorrateo de gastos recurrentes.
3. **Mis Cuentas** — cuentas individuales y conjuntas, saldos y conciliación.
4. **Herramientas de Hipoteca** — calculadora y análisis de viabilidad:
   - programa *Mi Primera Vivienda* de la Comunidad de Madrid (LTV de hasta el 95%);
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

```bash
mvn clean verify
```

```bash
java -jar backend/bootstrap/target/gastos.jar
```

```bash
docker compose -f infra/docker-compose.yml up --build
```

## Documentación

- [Arquitectura](docs/ARCHITECTURE.md)
- [ADR-0001 — Monolito modular frente a microservicios](docs/adr/ADR-0001-monolito-modular-vs-microservicios.md)
- [ADR-0002 — Plataforma de despliegue gratuita](docs/adr/ADR-0002-plataforma-de-despliegue.md)
- [Flujo de trabajo Git](docs/GIT_WORKFLOW.md)
- [Seguridad y cumplimiento](docs/SECURITY.md)

## Aviso

Las cifras del programa *Mi Primera Vivienda* y los tipos impositivos incluidos son
parámetros de trabajo configurables, no asesoramiento financiero. Contrástalos con la
normativa vigente de la Comunidad de Madrid y con tu entidad antes de tomar decisiones.
