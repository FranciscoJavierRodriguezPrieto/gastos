-- Prevision de ahorro: una por hogar.
--
-- Se guarda el plan entero como un documento JSON y no repartido en tablas. El plan es
-- una entrada de formulario que se lee y se escribe siempre entera, nunca se consulta por
-- dentro, y la prevision se recalcula al vuelo a partir de el. Un esquema relacional
-- serian cuatro tablas para nada.
--
-- varchar y no jsonb: el perfil de test corre en H2 y el esquema se mantiene en SQL
-- estandar (ver application-test.yml). La validacion la hace la API al recibirlo.
create table savings_plan (
    household_id uuid           primary key,
    document     varchar(20000) not null,
    updated_at   timestamp      not null
);
