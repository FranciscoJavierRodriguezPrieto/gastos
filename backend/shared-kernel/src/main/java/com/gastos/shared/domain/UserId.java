package com.gastos.shared.domain;

import java.util.UUID;

/**
 * Identificador opaco de usuario. Se usa UUID v4 y nunca un entero autoincremental:
 * evita la enumeracion de recursos y reduce la superficie de ataque BOLA/IDOR
 * (OWASP API1). La autorizacion se comprueba ademas en cada caso de uso.
 */
public record UserId(UUID value) {

    public UserId {
        Guard.notNull(value, "value");
    }

    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String value) {
        try {
            return new UserId(UUID.fromString(Guard.notBlank(value, "value")));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Identificador de usuario invalido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
