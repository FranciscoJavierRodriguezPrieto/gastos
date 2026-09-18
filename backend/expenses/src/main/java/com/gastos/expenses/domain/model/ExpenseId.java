package com.gastos.expenses.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import java.util.UUID;

public record ExpenseId(UUID value) {

    public ExpenseId {
        Guard.notNull(value, "value");
    }

    public static ExpenseId newId() {
        return new ExpenseId(UUID.randomUUID());
    }

    public static ExpenseId of(String value) {
        try {
            return new ExpenseId(UUID.fromString(Guard.notBlank(value, "value")));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Identificador de gasto invalido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
