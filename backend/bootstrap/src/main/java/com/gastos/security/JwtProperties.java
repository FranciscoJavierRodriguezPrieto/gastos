package com.gastos.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de los tokens.
 *
 * <p>La clave de firma llega por variable de entorno y no tiene valor por defecto en
 * produccion: una clave por defecto en el codigo es una clave publica, y quien la conozca
 * puede fabricarse un token para cualquier hogar.</p>
 *
 * @param secret         clave HMAC, minimo 32 bytes
 * @param issuer         emisor que se graba en el token
 * @param accessTokenTtl vida del token de acceso; corta porque no se puede revocar
 * @param refreshTokenTtl vida del token de refresco
 */
@ConfigurationProperties(prefix = "gastos.security.jwt")
public record JwtProperties(String secret,
                            String issuer,
                            Duration accessTokenTtl,
                            Duration refreshTokenTtl) {

    /** HS256 exige al menos 256 bits de clave. */
    public static final int MIN_SECRET_LENGTH = 32;

    public JwtProperties {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "gastos.security.jwt.secret debe tener al menos " + MIN_SECRET_LENGTH
                            + " caracteres. Define la variable de entorno JWT_SECRET con un valor "
                            + "aleatorio; sin ella la aplicacion no arranca a proposito.");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "gastos";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofDays(30);
        }
    }
}
