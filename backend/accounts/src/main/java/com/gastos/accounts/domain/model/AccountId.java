package com.gastos.accounts.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Guard.notNull(value, "value");
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }

    public static AccountId of(String value) {
        try {
            return new AccountId(UUID.fromString(Guard.notBlank(value, "value")));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Identificador de cuenta invalido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
