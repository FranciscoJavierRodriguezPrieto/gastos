-- Passkeys (WebAuthn).
--
-- Aqui no se guarda ningun secreto: solo la clave publica del autenticador. Quien se
-- lleve estas dos tablas enteras no puede suplantar a nadie, porque la clave privada no
-- sale nunca del dispositivo. Es la diferencia de fondo con user_credential.
--
-- El material criptografico va como texto en base64url y no como binario a proposito:
-- PostgreSQL lo pondria en bytea y H2 en varbinary, y esa divergencia haria que los tests
-- dejaran de validar el esquema que se despliega de verdad.

create table passkey_credential (
    id                       uuid          primary key,
    user_id                  uuid          not null references app_user (id) on delete cascade,
    -- Identificador que asigna el autenticador. Unico en toda la instalacion: al entrar
    -- es lo unico que se recibe, y a partir de el se descubre de quien es.
    credential_id            varchar(512)  not null,
    attested_credential_data varchar(2048) not null,
    -- Contador de firmas. Solo sirve para detectar autenticadores clonados; las passkeys
    -- sincronizadas entre dispositivos lo dejan siempre a cero, y eso no es sospechoso.
    signature_count          bigint        not null,
    -- Indicador BE del estandar: la credencial puede copiarse a la nube del fabricante.
    -- No cambia nunca, y por eso se puede comprobar que sigue igual en cada acceso.
    backup_eligible          boolean       not null,
    -- Nombre que pone la persona para reconocer el aparato al darlo de baja.
    label                    varchar(60)   not null,
    created_at               timestamp     not null,
    last_used_at             timestamp
);

create unique index uq_passkey_credential_id on passkey_credential (credential_id);
create index idx_passkey_credential_user on passkey_credential (user_id);

-- Retos de las ceremonias. Existe esta tabla porque la API es sin estado y no hay sesion
-- de servidor donde dejar el reto entre las dos llamadas de la ceremonia.
--
-- El reto se guarda EN CLARO, al contrario que los tokens de las otras tablas. No es una
-- credencial: es un numero aleatorio que hay que devolver firmado, y el servidor necesita
-- el valor original para compararlo. Conocerlo no sirve de nada sin la clave privada.
create table passkey_challenge (
    id         uuid         primary key,
    challenge  varchar(128) not null,
    -- REGISTRO o ACCESO. Un reto emitido para dar de alta una passkey no puede
    -- presentarse en el endpoint de acceso, que es publico.
    ceremony   varchar(20)  not null,
    -- Nulo en el acceso: el reto se emite antes de saber quien va a firmarlo.
    user_id    uuid         references app_user (id) on delete cascade,
    issued_at  timestamp    not null,
    expires_at timestamp    not null,
    used_at    timestamp
);

create unique index uq_passkey_challenge on passkey_challenge (challenge);
-- El barrido de caducados recorre por fecha.
create index idx_passkey_challenge_expires on passkey_challenge (expires_at);
