package com.gastos.shared.domain;

import java.util.Collection;

/**
 * Validaciones de invariantes de dominio. Fallar rapido y en el constructor del
 * value object es la primera linea de defensa frente a datos corruptos
 * (OWASP A03 - inyeccion: ningun valor no validado llega a un puerto de salida).
 */
public final class Guard {

    private Guard() {
    }

    public static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new DomainException("'" + field + "' es obligatorio");
        }
        return value;
    }

    public static String notBlank(String value, String field) {
        notNull(value, field);
        if (value.isBlank()) {
            throw new DomainException("'" + field + "' no puede estar vacio");
        }
        return value;
    }

    public static <T> Collection<T> notEmpty(Collection<T> value, String field) {
        notNull(value, field);
        if (value.isEmpty()) {
            throw new DomainException("'" + field + "' no puede estar vacio");
        }
        return value;
    }

    public static int positive(int value, String field) {
        if (value <= 0) {
            throw new DomainException("'" + field + "' debe ser mayor que cero, recibido: " + value);
        }
        return value;
    }

    public static int inRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new DomainException(
                    "'" + field + "' debe estar entre " + min + " y " + max + ", recibido: " + value);
        }
        return value;
    }
}
