package com.gastos.shared.domain;

import java.util.UUID;

/**
 * Identificador del hogar: es el limite de aislamiento multi-tenant. Toda consulta
 * de cuentas, gastos y simulaciones se filtra por este valor antes de devolver
 * datos (defensa sistematica frente a IDOR).
 */
public record HouseholdId(UUID value) {

    public HouseholdId {
        Guard.notNull(value, "value");
    }

    public static HouseholdId newId() {
        return new HouseholdId(UUID.randomUUID());
    }

    public static HouseholdId of(String value) {
        try {
            return new HouseholdId(UUID.fromString(Guard.notBlank(value, "value")));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Identificador de hogar invalido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
