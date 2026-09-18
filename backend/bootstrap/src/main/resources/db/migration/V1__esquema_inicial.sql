-- Esquema inicial de Gastos.
--
-- El esquema se versiona con Flyway y no se genera desde las entidades JPA: asi el
-- cambio de base de datos es codigo revisable en un pull request y reproducible en
-- cualquier entorno, en vez de un efecto secundario de una anotacion.
--
-- Convenciones:
--   * los importes son numeric(15,2): el dinero jamas usa coma flotante;
--   * los porcentajes son numeric(7,4), la escala de Percentage en el dominio;
--   * household_id va indexado en todas las tablas porque es el limite de aislamiento
--     multi-tenant y toda consulta lo filtra.
--
-- No hay tabla de usuarios ni de hogares todavia: el contexto iam aun no tiene puerto
-- de persistencia. Por eso household_id y los identificadores de usuario se guardan sin
-- clave ajena; se anadiran cuando llegue feature/security-jwt-passkeys.

-- ---------------------------------------------------------------- cuentas
create table account (
    id                 uuid          primary key,
    household_id       uuid          not null,
    alias              varchar(60)   not null,
    bank_name          varchar(60)   not null,
    type               varchar(20)   not null,
    ownership          varchar(20)   not null,
    balance            numeric(15,2) not null,
    balance_updated_at timestamp     not null
);

create index idx_account_household on account (household_id);

-- Los titulares son una coleccion del agregado, no una entidad propia: no tienen
-- identidad ni ciclo de vida fuera de la cuenta, de ahi el borrado en cascada.
create table account_holder (
    account_id uuid not null references account (id) on delete cascade,
    user_id    uuid not null,
    primary key (account_id, user_id)
);

-- ---------------------------------------------------------------- gastos
create table expense (
    id            uuid          primary key,
    household_id  uuid          not null,
    registered_by uuid          not null,
    description   varchar(140)  not null,
    amount        numeric(15,2) not null,
    category      varchar(20)   not null,
    recurrence    varchar(20)   not null,
    incurred_on   date          not null,
    account_id    uuid
);

-- La consulta dominante es "gastos de este hogar en este mes".
create index idx_expense_household_date on expense (household_id, incurred_on);

-- ---------------------------------------------------------------- programas de ayuda
create table aid_program (
    id                  uuid          primary key,
    household_id        uuid          not null,
    name                varchar(80)   not null,
    max_loan_to_value   numeric(7,4)  not null,
    -- Nulo significa "sin limite", que es distinto de cero.
    max_property_price  numeric(15,2),
    max_applicant_age   integer,
    requires_first_home boolean       not null,
    active              boolean       not null,
    source_note         varchar(300)  not null
);

create index idx_aid_program_household on aid_program (household_id);

-- ---------------------------------------------------------------- escenarios de hipoteca
-- Se guarda la ENTRADA de la simulacion, nunca el resultado: los tipos y las politicas
-- cambian, y una cuota calculada hace meses seria un dato falso. Por eso no hay columnas
-- de cuota, DTI ni veredicto.
create table mortgage_scenario (
    id                   uuid          primary key,
    household_id         uuid          not null,
    name                 varchar(60)   not null,
    property_price       numeric(15,2) not null,
    available_savings    numeric(15,2) not null,
    target_reserve       numeric(15,2) not null,
    annual_nominal_rate  numeric(7,4)  not null,
    term_years           integer       not null,
    net_monthly_income   numeric(15,2) not null,
    other_monthly_debts  numeric(15,2) not null,
    applicant_age        integer       not null,
    first_home           boolean       not null,
    financing_mode       varchar(20)   not null,
    financing_program_id uuid,
    manual_loan_to_value numeric(7,4),
    created_at           timestamp     not null,
    updated_at           timestamp     not null
);

create index idx_mortgage_scenario_household on mortgage_scenario (household_id);
