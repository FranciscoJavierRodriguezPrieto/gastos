package com.gastos.expenses.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import java.util.UUID;

public record FixedExpenseId(UUID value) {

    public FixedExpenseId {
        Guard.notNull(value, "value");
    }

    public static FixedExpenseId newId() {
        return new FixedExpenseId(UUID.randomUUID());
    }

    public static FixedExpenseId of(String value) {
        try {
            return new FixedExpenseId(UUID.fromString(Guard.notBlank(value, "value")));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Identificador de gasto fijo invalido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
