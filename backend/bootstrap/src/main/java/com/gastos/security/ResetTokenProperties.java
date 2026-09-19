package com.gastos.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del restablecimiento de contrasena.
 *
 * @param ttl     vida del enlace; corta porque viaja por un canal que no controlamos
 * @param baseUrl direccion del frontend a la que apunta el enlace del correo
 * @param from    remitente. En Brevo tiene que ser un remitente verificado, o el envio
 *                se rechaza
 */
@ConfigurationProperties(prefix = "gastos.password-reset")
public record ResetTokenProperties(Duration ttl, String baseUrl, String from) {

    public ResetTokenProperties {
        if (ttl == null) {
            ttl = Duration.ofMinutes(30);
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:5173";
        }
        if (from == null || from.isBlank()) {
            from = "gastos@localhost";
        }
        // Sin barra final, para no generar enlaces con doble barra.
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
