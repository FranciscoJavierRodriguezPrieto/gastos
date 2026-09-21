-- Normativa de vivienda de la Comunidad de Madrid vigente desde agosto de 2026.
--
-- Orden de 27 de julio de 2026 (BOCM num. 186, 6 de agosto de 2026), que regula de nuevo
-- el programa Mi Primera Vivienda y deroga la Orden 2350/2022. Cambia tres cosas que la
-- aplicacion no sabia representar:
--
--   1. Tramos por edad: 100% hasta 40 anos, 95% hasta 45 y 90% hasta 50.
--   2. Acceso al 100% SIN limite de edad para familias numerosas, monoparentales o con
--      hijos menores a cargo. Hace falta saber si un programa lo exige y si el hogar lo
--      cumple.
--   3. Precio maximo de 425.000 EUR (antes 390.000).
--
-- Ademas el ITP de Madrid distingue familia numerosa (4%) y vivienda habitual (bonificacion
-- del 10% hasta 250.000 EUR), y el escenario guardado tiene que recordarlo.

alter table aid_program add column requires_family boolean not null default false;

alter table mortgage_scenario add column family_with_children boolean not null default false;
alter table mortgage_scenario add column large_family         boolean not null default false;
-- Por defecto SI: es el caso de quien simula su primera vivienda, y era lo que el
-- simulador suponia implicitamente hasta ahora.
alter table mortgage_scenario add column primary_residence    boolean not null default true;

-- El catalogo de partida anterior queda derogado. No se borra, porque son datos del hogar
-- y puede que alguien los haya retocado: se DESACTIVA, se marca en el nombre y se explica
-- en la nota. Al instalar de nuevo el catalogo desde la aplicacion llegan los programas
-- vigentes, que tienen otros nombres y no chocan con estos.
update aid_program
   set active = false,
       name = name || ' (derogado)',
       source_note = 'Derogado por la Orden de 27/07/2026 (BOCM 186, 06/08/2026). Instala el '
                  || 'catalogo actualizado desde Programas de ayuda.'
 where name in ('Mi Primera Vivienda (Comunidad de Madrid)',
                'Aval hasta el 100% para menores de 40 (por verificar)');
