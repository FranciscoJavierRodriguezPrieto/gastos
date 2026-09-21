package com.gastos.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de las invitaciones al hogar.
 *
 * @param ttl     vida del codigo. Dias y no minutos como el de restablecimiento: aquel
 *                se pide y se usa seguido, y este tiene que aguantar a que la otra
 *                persona lo vea y se siente a rellenar sus datos
 * @param baseUrl direccion publica del frontend, a la que apunta el enlace de alta. Es
 *                la misma {@code APP_BASE_URL} que usa el correo de restablecimiento: un
 *                solo valor que mantener al desplegar
 */
@ConfigurationProperties(prefix = "gastos.invitation")
public record InvitationProperties(Duration ttl, String baseUrl) {

    public InvitationProperties {
        if (ttl == null) {
            ttl = Duration.ofDays(7);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:5173";
        }
        // Sin barra final, para no generar enlaces con doble barra.
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
