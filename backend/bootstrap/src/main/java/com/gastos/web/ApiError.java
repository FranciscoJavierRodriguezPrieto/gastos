package com.gastos.web;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo unico de error de la API.
 *
 * <p>Un solo formato para todos los fallos: el cliente escribe el manejo de errores una
 * vez. Nunca contiene trazas, nombres de clase ni detalles de la base de datos
 * (OWASP API8: una configuracion que filtra internos es una vulnerabilidad).</p>
 *
 * @param details lista opcional de errores de validacion, campo a campo
 */
public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String message,
                       String path,
                       List<String> details) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError of(int status, String error, String message, String path,
                              List<String> details) {
        return new ApiError(Instant.now(), status, error, message, path, List.copyOf(details));
    }
}
