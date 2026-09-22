-- Restablecimiento de contrasena por correo.
--
-- Cierra la carencia mas seria que quedaba en el manual de operacion: hasta ahora,
-- olvidar la contrasena obligaba a cambiar el hash a mano en la base de datos.
--
-- Se guarda el HASH del token, nunca el token que viaja en el enlace: quien lea esta
-- tabla no obtiene nada utilizable.

create table password_reset_token (
    id         uuid        primary key,
    user_id    uuid        not null references app_user (id) on delete cascade,
    token_hash varchar(64) not null,
    issued_at  timestamp   not null,
    expires_at timestamp   not null,
    -- Un solo uso: al consumirlo se sella aqui la fecha.
    used_at    timestamp
);

create unique index uq_password_reset_token_hash on password_reset_token (token_hash);
-- La invalidacion en bloque busca por usuario.
create index idx_password_reset_token_user on password_reset_token (user_id);
