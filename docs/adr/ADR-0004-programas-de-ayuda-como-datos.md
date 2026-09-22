# ADR-0004: Los programas de ayuda son datos, no código

- **Estado:** Aceptada
- **Fecha:** 2026-09-18
- **Rama:** `refactor/mortgage-programas-configurables`
- **Sustituye a:** la clase `MiPrimeraViviendaPolicy` de `feature/architecture-setup`

## Contexto

El LTV máximo se decidía en una clase Java con las condiciones del programa *Mi Primera
Vivienda* escritas dentro: 95%, precio máximo 390.000 €, edad máxima 35.

Tres problemas:

1. **Cada cambio normativo era un despliegue.** Las convocatorias se revisan, y sus
   cifras exactas hay que contrastarlas con el boletín oficial.
2. **Sólo cabía un programa.** Conviven varios a la vez (el aval autonómico, ayudas
   estatales, líneas de la propia entidad) y el hogar puede cumplir más de uno.
3. **No había salida de emergencia.** Si el banco ofrecía un 90% que no encajaba con
   ninguna regla codificada, no se podía simular.

## Decisión

**El catálogo de programas pasa a ser un agregado editable por hogar** (`AidProgram`),
con CRUD completo en la API. Un programa declara su LTV máximo y sus requisitos —precio
máximo, edad máxima, primera vivienda— donde `null` significa "sin límite".

Y el simulador gana **tres modos de financiación**:

| Modo | Comportamiento |
|---|---|
| `AUTOMATICO` | Evalúa todos los programas activos y aplica el que más financie de entre los que se cumplan. |
| `PROGRAMA` | Aplica el programa elegido. Si no se cumple, **no fuerza el LTV**: explica qué requisito falta y aplica la financiación estándar. |
| `MANUAL` | El usuario fija el LTV y no se comprueba ningún requisito. Es la válvula de escape. |

Dos reglas que sostienen el diseño:

- **El cálculo financiero es idéntico en los tres modos.** Un programa sólo decide
  cuánto presta el banco. Cuota, DTI y veredicto se calculan igual venga el LTV de una
  convocatoria autonómica, del 80% estándar o de un número escrito a mano. Hay un test
  que lo comprueba (`sameLoanToValueSameNumbers`).
- **Un programa nunca empeora la financiación estándar.** Si su LTV no supera el 80%,
  se aplica el estándar.

Toda la variabilidad normativa queda encerrada en `FinancingSelector`. Del paso
siguiente en adelante el motor trabaja con un `Percentage` y le da igual su origen.

## Consecuencias

**Positivas**

- Añadir una convocatoria nueva es rellenar un formulario, no programar.
- El hogar ve **por qué** sale ese LTV: qué programas cumple, cuáles no y qué requisito
  concreto falla en cada uno.
- El modo MANUAL cubre cualquier caso no previsto sin que haya que modelarlo.
- Los escenarios guardados se recalculan con el catálogo vigente: corregir las
  condiciones de un programa actualiza automáticamente las simulaciones guardadas.

**Negativas y mitigaciones**

- *Las cifras las introduce el usuario, así que pueden estar mal.* Cada programa lleva
  un campo `sourceNote` para anotar de dónde salen y qué falta verificar.
- *El endpoint de simulación ahora exige `X-Household-Id`*, porque el catálogo depende
  del hogar. Es un cambio incompatible en la API, asumible porque todavía no hay
  cliente.
- *Un catálogo vacío da 80%.* Es el comportamiento correcto: sin ayuda declarada, la
  financiación es la ordinaria.

## Sobre el catálogo de partida

`ReferenceAidPrograms` instala dos plantillas para no empezar con la pantalla en blanco.
**Sus cifras no están verificadas.** Por eso las plantillas de las que no se conocen las
condiciones exactas —como el aval del 100% para menores de 40— **se instalan
desactivadas**: una cifra sin comprobar que participa en el cálculo es peor que no tener
el programa, porque produce un resultado con apariencia de certeza.
