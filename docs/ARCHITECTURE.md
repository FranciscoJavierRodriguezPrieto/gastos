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
├── application    → casos de uso; orquestan, no calculan
└── infrastructure → adaptadores (JPA, REST, seguridad)  [ramas posteriores]
```

**Regla de dependencia:** siempre hacia dentro. `infrastructure → application → domain`.
El dominio no conoce a nadie. Esto no es una convención de estilo: está verificado en
`HexagonalArchitectureTest` y rompe el build si se incumple.

## Reglas de arquitectura verificadas en CI

| Regla | Motivo |
|---|---|
| `domain` no depende de `org.springframework` | el núcleo financiero se testea sin framework |
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
(ITP 6% + 4% de gastos), `MiPrimeraViviendaPolicy` (LTV 95%, precio y edad máximos) y
`LendingPolicy` (DTI 30% vivienda / 40% total) son objetos configurables. La normativa
autonómica cambia; el código no debería.

**El resultado de una simulación no se persiste, sólo su entrada.** `MortgageScenario`
guarda el `SimulationRequest`. Los tipos y las políticas cambian, así que el resultado
se recalcula siempre con el motor vigente.

**El aislamiento por hogar está en la firma de los puertos.** Todo método de repositorio
exige `HouseholdId`. El aislamiento multi-tenant no depende de que el programador de
turno recuerde añadir el filtro.

## Flujo del motor de hipoteca

```
SimulationRequest
      │
      ├─ 1. UpfrontCosts          ITP (6%) + notaría/registro/gestoría (4%)
      ├─ 2. LTV aplicable         MiPrimeraViviendaPolicy → 95% si es elegible, 80% si no
      ├─ 3. FinancingPlan         reserva → gastos → entrada → préstamo
      ├─ 4. AmortizationCalculator cuota mensual (sistema francés)
      └─ 5. ViabilityAnalyzer     DTI vivienda / DTI total / renta disponible / colchón
                                            │
                                            ▼
                         INVIABLE · VIABLE_AJUSTADA · ÓPTIMA
```

> **Aviso sobre los parámetros por defecto.** Los valores del programa *Mi Primera
> Vivienda* (LTV 95%, precio máximo 390.000 €, edad máxima 35) y el tipo de ITP son una
> configuración de trabajo. Deben contrastarse con la convocatoria vigente publicada por
> la Comunidad de Madrid antes de usarlos para decidir una compra real. El código está
> preparado para cambiarlos sin recompilar.

## Compilar y probar

```bash
mvn clean verify
```

```bash
java -jar backend/bootstrap/target/gastos.jar
```
