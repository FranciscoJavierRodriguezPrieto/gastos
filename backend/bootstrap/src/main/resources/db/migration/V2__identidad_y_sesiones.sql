-- Identidad: hogares, usuarios, credenciales y sesiones.
--
-- Llega en V2 y no en V1 porque hasta ahora la identidad viajaba en una cabecera sin
-- firmar. Esta migracion es la que permite cerrar OWASP API2.

create table household (
    id   uuid        primary key,
    name varchar(60) not null
);

-- "app_user" y no "user": USER es palabra reservada en SQL estandar y en PostgreSQL
-- obligaria a entrecomillarla en cada consulta.
create table app_user (
    id                 uuid          primary key,
    household_id       uuid          not null references household (id),
    email              varchar(254)  not null,
    display_name       varchar(60)   not null,
    role               varchar(20)   not null,
    monthly_net_income numeric(15,2) not null
);

-- El correo identifica al usuario al autenticar: tiene que ser unico en la instalacion.
create unique index uq_app_user_email on app_user (email);
create index idx_app_user_household on app_user (household_id);

-- Tabla aparte de app_user a proposito: casi ninguna consulta del sistema necesita el
-- hash, y separarlo reduce las probabilidades de que acabe donde no debe.
create table user_credential (
    user_id       uuid         primary key references app_user (id) on delete cascade,
    password_hash varchar(200) not null,
    updated_at    timestamp    not null
);

-- Se guarda el HASH del token de refresco, nunca el token: si alguien lee esta tabla no
-- obtiene credenciales utilizables. replaced_by encadena las rotaciones, de modo que al
-- detectar un token reutilizado se pueda seguir el rastro.
create table refresh_token (
    id          uuid        primary key,
    user_id     uuid        not null references app_user (id) on delete cascade,
    token_hash  varchar(64) not null,
    issued_at   timestamp   not null,
    expires_at  timestamp   not null,
    revoked_at  timestamp,
    replaced_by uuid
);

create unique index uq_refresh_token_hash on refresh_token (token_hash);
-- El refresco busca por hash; la revocacion en cascada, por usuario.
create index idx_refresh_token_user on refresh_token (user_id);
