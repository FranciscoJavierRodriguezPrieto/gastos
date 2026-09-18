# ADR-0003: No almacenar datos bancarios identificativos

- **Estado:** Aceptada
- **Fecha:** 2026-09-18
- **Rama:** `refactor/quitar-iban`
- **Sustituye a:** la decisión implícita de la rama `feature/architecture-setup`, donde
  se añadió un IBAN obligatorio sin que estuviera en los requisitos

## Contexto

El modelo de `Account` incluía un IBAN obligatorio, validado con el algoritmo mod-97 y
expuesto sólo de forma enmascarada. No figuraba en los requisitos del producto: se
introdujo al modelar "gestión de cuentas bancarias del hogar", asumiendo que hacía falta.

Al revisarlo, no cumple ninguna función real:

- Los saldos los introduce el usuario a mano. **No hay integración bancaria** ni está
  prevista a corto plazo.
- Para distinguir entre cuentas, el alias y el nombre del banco son suficientes y más
  legibles que un IBAN.
- Nada en la aplicación lee el IBAN: ni la conciliación, ni el dashboard, ni el
  simulador de hipoteca.

A cambio, su coste es real y permanente:

- Es un **dato personal identificativo** sujeto al RGPD.
- Obliga a cifrado a nivel de columna, gestión de claves y rotación.
- Amplía el impacto de cualquier fuga: un saldo sin IBAN es un número; un saldo con
  IBAN identifica a una persona y su entidad.
- Arrastra código de validación, enmascarado y sus tests.

## Decisión

**Se elimina el IBAN del modelo, de la API y de la base de datos.** Una cuenta queda
descrita por alias, banco, tipo, titularidad, titulares y saldo.

## Consecuencias

**Positivas**

- El dato más sensible del sistema deja de existir. La defensa más barata frente a una
  fuga es no tener nada que filtrar.
- Desaparece el trabajo de cifrado de columna previsto para
  `feature/persistence-postgresql`.
- Menos código: se borran `Iban`, su validación mod-97, el enmascarado y sus tests.

**Negativas**

- Si algún día se integra agregación bancaria (PSD2), habrá que reintroducirlo. La
  vuelta atrás es acotada: un value object, un campo en el DTO y una columna cifrada.
- Quien tenga varias cuentas en el mismo banco depende del alias para distinguirlas.
  Aceptable: el alias lo elige el propio usuario.

## Lección

El modelado por analogía —"esto es una cuenta bancaria, luego lleva IBAN"— añade campos
que nadie pidió. La pregunta correcta ante cada dato personal es **qué función cumple
hoy**, no si resulta plausible que aparezca en ese tipo de entidad.
