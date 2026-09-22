package com.gastos.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del limitador de peticiones.
 *
 * @param enabled       permite desactivarlo en tests y en desarrollo
 * @param maxRequests   peticiones permitidas por ventana y cliente
 * @param windowSeconds duracion de la ventana en segundos
 */
@ConfigurationProperties(prefix = "gastos.rate-limit")
public record RateLimitProperties(boolean enabled, int maxRequests, int windowSeconds) {

    public RateLimitProperties {
        if (maxRequests <= 0) {
            maxRequests = 120;
        }
        if (windowSeconds <= 0) {
            windowSeconds = 60;
        }
    }
}
