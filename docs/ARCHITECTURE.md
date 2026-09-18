# Arquitectura

## Visión general

Monolito modular con arquitectura hexagonal (puertos y adaptadores). Un solo artefacto
ejecutable, fronteras internas duras entre contextos acotados. La justificación está en
[ADR-0001](adr/ADR-0001-monolito-modular-vs-microservicios.md).

```
                    ┌──────────────────────────────┐
                    │   PWA (Cloudflare Pages)     │
                    └──────────────┬───────────────┘
                                   │ HTTPS / JWT
                    ┌──────────────▼───────────────┐
                    │        bootstrap             │  ← raíz de composición
                    │  (Spring Boot, REST, config) │
                    └──┬────────┬────────┬─────────┘
                       │        │        │
              ┌────────▼──┐ ┌───▼─────┐ ┌▼──────────┐ ┌──────────┐
              │ accounts  │ │ expenses│ │ mortgage  │ │   iam    │
              └────────┬──┘ └───┬─────┘ └┬──────────┘ └────┬─────┘
                       └────────┴────────┴─────────────────┘
                                   │
                          ┌────────▼────────┐
                          │  shared-kernel  │
                          └─────────────────┘
```

## El hexágono dentro de cada contexto

```
com.gastos.<contexto>
├── domain
│   ├── model      → agregados y value objects (Java puro, sin framework)
│   ├── port       → interfaces que el dominio necesita (repositorios, servicios)
│   ├── policy     → parámetros de negocio configurables (tipos, umbrales)
│   └── service    → servicios de dominio sin estado (cálculos)
├── application
│   ├── *UseCase   → casos de uso; orquestan, no calculan
│   └── *Command   → órdenes en tipos de dominio, independientes del contrato HTTP
└── infrastructure
    ├── rest            → controladores y mappers
    │   └── dto         → contrato HTTP: records inmutables con validación
    └── persistence/jpa → entidades, mappers y adaptadores de los puertos
```

**La traducción vive en un solo sitio.** El flujo completo de una petición es
`DTO → mapper → command → caso de uso → dominio`, y de vuelta `dominio → mapper → DTO`.
El dominio no conoce los DTO y los DTO no conocen el dominio; el mapper es el único que
ve ambos lados, así que cuando un campo sale mal en la API se sabe exactamente en qué
fichero mirar.

**Regla de dependencia:** siempre hacia dentro. `infrastructure → application → domain`.
El dominio no conoce a nadie. Esto no es una convención de estilo: está verificado en
`HexagonalArchitectureTest` y rompe el build si se incumple.

## Reglas de arquitectura verificadas en CI

| Regla | Motivo |
|---|---|
| `domain` no depende de `org.springframework` | el núcleo financiero se testea sin framework |
| `application` no depende de `org.springframework` | los casos de uso se instancian a mano en la raíz de composición |
| `domain` ni `application` dependen de `..rest..` | el contrato HTTP no condiciona al modelo |
| los DTO de `..rest.dto..` son records | un DTO mutable invita a reutilizarlo como modelo |
| `domain` no depende de `jakarta.persistence` | el modelo no queda atado al esquema de BD |
| `domain` no depende de `application` ni de `infrastructure` | las dependencias apuntan hacia dentro |
| `application` no depende de `infrastructure` | los casos de uso hablan con puertos |
| `mortgage` no depende de `accounts`, `expenses` ni `iam` | los contextos se comunican por contratos |
| `domain` y `application` no llaman a `Instant.now()` ni `LocalDate.now()` | el tiempo se inyecta como `Clock`: tests deterministas |

## Decisiones de modelado destacables

**El dinero nunca es `double`.** `Money` encapsula `BigDecimal` con escala 2 y redondeo
`HALF_UP`. Los pasos intermedios (amortización francesa) usan `MathContext.DECIMAL64` y
sólo el resultado final se redondea: redondear antes acumula euros de desviación a lo
largo de 30 años.

**Los umbrales de negocio son políticas, no constantes.** `PurchaseCostsPolicy`
(ITP 6% + 4% de gastos) y `LendingPolicy` (DTI 30% vivienda / 40% total, LTV estándar
80%) son objetos configurables.

**Y los programas de ayuda ni siquiera son código: son datos.** `AidProgram` es un
agregado editable desde la aplicación, con CRUD propio, porque las convocatorias cambian
y conviven varias a la vez ([ADR-0004](adr/ADR-0004-programas-de-ayuda-como-datos.md)).

**El resultado de una simulación no se persiste, sólo su entrada.** `MortgageScenario`
guarda el `SimulationRequest`. Los tipos y las políticas cambian, así que el resultado
se recalcula siempre con el motor vigente.

**El aislamiento por hogar está en la firma de los puertos.** Todo método de repositorio
exige `HouseholdId`, y las consultas de Spring Data también (`findByIdAndHouseholdId`).
El aislamiento multi-tenant no depende de que el programador de turno recuerde añadir el
filtro.

**Las entidades JPA no son el modelo.** `AccountEntity` es una fila; `Account` es el
agregado. JPA necesita constructor vacío y setters, que es justo lo que un agregado no
debe ofrecer, así que se mantienen separados y un mapper traduce. Cuesta una clase por
agregado y a cambio el esquema de la base de datos no dicta las invariantes de negocio.

**El esquema lo gobierna Flyway, no Hibernate.** `ddl-auto: validate`: si una entidad y
el esquema divergen, la aplicación no arranca. Mejor un fallo al desplegar que una
columna ignorada en silencio.

## Flujo del motor de hipoteca

```
SimulationRequest
      │
      ├─ 1. UpfrontCosts          ITP (6%) + notaría/registro/gestoría (4%)
      ├─ 2. FinancingSelector     AUTOMATICO / PROGRAMA / MANUAL → LTV aplicable
      ├─ 3. FinancingPlan         reserva → gastos → entrada → préstamo
      ├─ 4. AmortizationCalculator cuota mensual (sistema francés)
      └─ 5. ViabilityAnalyzer     DTI vivienda / DTI total / renta disponible / colchón
                                            │
                                            ▼
                         INVIABLE · VIABLE_AJUSTADA · ÓPTIMA
```

**Sólo el paso 2 conoce los programas de ayuda.** Del 3 en adelante el motor trabaja
con un `Percentage` y le da igual si viene de una convocatoria autonómica, de la
financiación estándar o de un número que el usuario escribió a mano. Aislar ahí la
variabilidad normativa es lo que permite añadir una convocatoria sin tocar una línea de
matemática financiera.

> **Aviso sobre las cifras.** Ni el catálogo de partida ni el tipo de ITP están
> verificados: son una configuración de trabajo. Deben contrastarse con la normativa
> vigente publicada por la Comunidad de Madrid antes de usarlos para decidir una compra
> real. Todos son editables desde la aplicación, sin recompilar.

## Compilar y probar

```bash
mvn clean verify
```

```bash
java -jar backend/bootstrap/target/gastos.jar
```
