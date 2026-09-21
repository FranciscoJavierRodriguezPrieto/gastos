# ADR-0008: Normativa de vivienda de Madrid de 2026

- **Estado:** Aceptada
- **Fecha:** 2026-09-21
- **Rama:** `fix/normativa-madrid-2026`

## Contexto

Al revisar los baremos contra fuentes oficiales aparecieron tres problemas:

1. **Mi Primera Vivienda había cambiado.** La Orden de 27 de julio de 2026 (BOCM
   nº 186, 6 de agosto de 2026, en vigor el 7 de agosto) deroga la Orden 2350/2022.
   El catálogo de la aplicación seguía con la anterior: 95 %, 390.000 € y 35 años.
2. **La rebaja del ITP estaba mal y además no se usaba.** El código tenía una variante
   del 5,4 % «para menores de 40» que no se aplicaba en ningún sitio. La bonificación real
   es del 10 % de la cuota para **cualquier** vivienda habitual de hasta 250.000 €, sin
   límite de edad. Tampoco estaba el 4 % de familia numerosa.
3. **Los baremos de `application.yml` no se leían.** El fichero decía «cambian por
   normativa, no por código», pero el simulador usaba cifras fijas.

## Lo que dice la norma (artículos 2 a 4 de la Orden)

| Acceso | Financiación máxima |
|---|---|
| Personas que no superen los 40 años | 100 % |
| Personas que no superen los 45 años | 95 % |
| Personas que no superen los 50 años | 90 % |
| Familias numerosas, monoparentales o con hijos menores a cargo, sin límite de edad | 100 % |

Precio máximo: 425.000 € sin gastos ni impuestos. Primera vivienda, residencia legal en
Madrid los dos años anteriores y vivir en ella al menos cinco años.

## Decisiones

**Un programa por tramo, no un programa con tramos.** El modelo ya sabía expresar «hasta
tal edad, tal porcentaje»; con cuatro programas y el modo automático —que elige el que
más financia de entre los que se cumplen— el hogar cae exactamente en su tramo. A los 42
se cumplen el de 45 y el de 50, y gana el de 45. Añadir tramos al agregado habría sido
más código para el mismo resultado.

**La edad que cuenta es la del mayor.** La Orden exige que *todas* las personas
adquirentes cumplan el límite. El formulario decía «edad del solicitante más joven», que
era justo al revés, y con dos compradores de 38 y 43 daba un 100 % que no existe.

**Tres datos nuevos del hogar**, todos opcionales en la API para no romper clientes:
familia con hijos, familia numerosa y vivienda habitual. Esta última vale «sí» por
defecto porque es lo que el simulador suponía hasta ahora.

**El catálogo antiguo se desactiva, no se borra.** La migración V5 lo apaga, le añade
«(derogado)» al nombre y explica el motivo en la nota. Son datos del hogar, y alguien
podría haberlos retocado.

**Lo que no se modela, se dice.** La residencia de dos años, la permanencia de cinco y la
bonificación del 100 % del ITP en municipios de menos de 2.500 habitantes no se
comprueban porque el simulador no tiene esos datos. Están escritos en la nota de cada
programa y en el código, para que nadie crea que se han verificado.

## Consecuencias

- Una vivienda habitual de 240.000 € paga ahora 12.960 € de ITP y no 14.400: 1.440 € menos
  de ahorro necesario, lo que puede cambiar el veredicto de una operación ajustada.
- Los tests fijan cada borde de la norma (40, 41, 45, 46, 50 y 51 años; 250.000 y
  250.001 €; 425.000 y 425.001 €). Si fallan, hay que releer la norma antes de tocar el
  número esperado.
- **Esto caduca.** Las ayudas autonómicas cambian cada uno o dos años. La nota de cada
  programa lleva la fecha y la norma para saber cuándo hay que volver a mirarlo.

Fuentes: [Orden de 27/07/2026, BOCM nº 186](https://www.comunidad.madrid/transparencia/sites/default/files/05_public_bocm_orden_mpv.pdf) ·
[Transmisiones Patrimoniales Onerosas](https://www.comunidad.madrid/atencion-contribuyente/transmisiones-patrimoniales-onerosas) ·
[Beneficios fiscales](https://www.comunidad.madrid/atencion-contribuyente/beneficios-fiscales)
