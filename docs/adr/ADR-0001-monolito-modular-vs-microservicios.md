# ADR-0001: Monolito modular frente a microservicios

- **Estado:** Aceptada
- **Fecha:** 2026-09-18
- **Contexto de decisión:** arquitectura del backend Java
- **Rama:** `feature/architecture-setup`

## Contexto

La aplicación da servicio a **dos usuarios** (un hogar). El volumen esperado es de
decenas de escrituras al día y picos de lectura triviales. El requisito duro no es la
escala, sino el **presupuesto de infraestructura**: el objetivo es desplegar en capas
gratuitas, donde la restricción real es la memoria RAM y el número de servicios
permitidos.

Los cuatro módulos funcionales (Resumen, Gastos, Mis Cuentas, Herramientas de
Hipoteca) comparten un mismo concepto raíz —el hogar— y sus datos se consultan casi
siempre juntos: el dashboard necesita, en la misma pantalla, saldos, gastos del mes y
la cuota simulada.

## Opciones consideradas

### A. Microservicios en Spring Boot (un servicio por contexto)

Cuatro servicios más un gateway. Cada JVM de Spring Boot consume del orden de
**200–350 MB** en reposo. Sumando el gateway, el suelo de memoria ronda 1–1,5 GB,
frente a los **256–512 MB** que ofrecen las capas gratuitas de Fly.io o Koyeb. Además
introduce latencia de red y consistencia eventual entre datos que el dashboard
necesita de forma transaccional, y multiplica por cinco la superficie de despliegue,
observabilidad y seguridad para un equipo de una persona.

### B. Monolito tradicional en capas

Una JVM, despliegue trivial. Pero una estructura por capas técnicas
(`controllers/services/repositories`) degrada de forma predecible: la lógica
financiera acaba repartida entre servicios anémicos y consultas, y los contextos se
acoplan sin que nadie lo note.

### C. Monolito modular con arquitectura hexagonal *(elegida)*

Una sola JVM y un solo artefacto desplegable, pero organizada en **módulos Maven por
contexto acotado**, cada uno con su hexágono interno (`domain` / `application` /
`infrastructure`). Las fronteras están impuestas por el grafo de dependencias de
Maven y verificadas por tests de ArchUnit que fallan el build.

## Decisión

Se adopta la **opción C: monolito modular (Modular Monolith) con arquitectura
hexagonal**, con esta estructura de módulos:

| Módulo | Responsabilidad | Depende de |
|---|---|---|
| `shared-kernel` | `Money`, `Percentage`, identificadores, invariantes | — |
| `iam` | Usuarios, hogar, roles | `shared-kernel` |
| `accounts` | Cuentas bancarias y saldos | `shared-kernel` |
| `expenses` | Gastos, categorías, resumen mensual | `shared-kernel` |
| `mortgage` | Amortización, gastos de compraventa, DTI, viabilidad | `shared-kernel` |
| `bootstrap` | Raíz de composición, configuración, artefacto ejecutable | todos |

## Consecuencias

**Positivas**

- Una sola JVM: cabe en 512 MB y arranca en segundos, compatible con escalado a cero.
- Transaccionalidad real entre contextos, sin sagas ni consistencia eventual.
- Las fronteras entre contextos son explícitas y **verificadas en CI** (ArchUnit), no
  confiadas a la disciplina.
- El núcleo financiero no depende de Spring: los 25 tests del contexto de hipoteca
  corren en milisegundos sin levantar contexto de aplicación.

**Negativas y mitigaciones**

- *Un fallo tumba toda la aplicación.* Aceptable: es una herramienta doméstica, no un
  sistema crítico.
- *Riesgo de acoplamiento con el tiempo.* Mitigado por los tests de arquitectura.
- *Escalado sólo vertical.* Irrelevante con dos usuarios.

**Puerta de salida:** si algún día un contexto necesitara escalar por separado, la
extracción es mecánica —ya es un módulo Maven con sus puertos definidos— y no exige
reescribir la lógica de negocio.
