-- Invitacion al hogar: el titular genera un codigo y la pareja se da de alta ella misma.
--
-- Sustituye al alta directa (POST /auth/members), en la que el titular elegia la
-- contrasena del otro conviviente. No hay migracion de datos que hacer: aquel endpoint no
-- dejaba rastro propio, solo creaba filas en app_user y user_credential, que siguen
-- siendo validas.

create table household_invitation (
    id           uuid        primary key,
    household_id uuid        not null references household (id) on delete cascade,
    -- Quien invita. Se guarda para poder decirle a quien llega de parte de quien viene.
    invited_by   uuid        not null references app_user (id) on delete cascade,
    -- SHA-256 en hexadecimal del codigo normalizado. El codigo en claro no se guarda:
    -- quien lea esta tabla no obtiene una llave del hogar, solo la prueba de que existio.
    code_hash    varchar(64) not null,
    issued_at    timestamp   not null,
    expires_at   timestamp   not null,
    accepted_at  timestamp,
    revoked_at   timestamp
);

-- El alta busca por hash: es la consulta del camino critico y la unica que se hace sin
-- sesion iniciada. Unico ademas de indice, porque dos invitaciones con el mismo hash
-- solo podrian venir de un fallo del generador.
create unique index uq_household_invitation_code on household_invitation (code_hash);

-- Para localizar la invitacion vigente de un hogar y para revocarlas en bloque.
create index idx_household_invitation_household on household_invitation (household_id);
