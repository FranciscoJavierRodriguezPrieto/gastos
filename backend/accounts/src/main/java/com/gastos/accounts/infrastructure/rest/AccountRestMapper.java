package com.gastos.accounts.infrastructure.rest;

import com.gastos.accounts.application.OpenAccountCommand;
import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountType;
import com.gastos.accounts.domain.model.Ownership;
import com.gastos.accounts.infrastructure.rest.dto.AccountRequest;
import com.gastos.accounts.infrastructure.rest.dto.AccountResponse;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Traduce entre el contrato HTTP del contexto de cuentas y el dominio. */
public final class AccountRestMapper {

    private AccountRestMapper() {
    }

    public static OpenAccountCommand toCommand(HouseholdId householdId, AccountRequest request) {
        Set<UserId> holders = request.holders().stream()
                .map(UserId::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new OpenAccountCommand(
                householdId,
                request.alias(),
                request.bankName(),
                parseType(request.type()),
                parseOwnership(request.ownership()),
                holders,
                Money.euros(request.initialBalance()));
    }

    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.id().value(),
                account.alias(),
                account.bankName(),
                account.type().name(),
                account.ownership().name(),
                account.holders().stream().map(UserId::value).collect(Collectors.toSet()),
                account.balance().amount(),
                account.balanceUpdatedAt());
    }

    public static List<AccountResponse> toResponses(List<Account> accounts) {
        return accounts.stream().map(AccountRestMapper::toResponse).toList();
    }

    private static AccountType parseType(String value) {
        try {
            return AccountType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Tipo de cuenta no valido. Valores admitidos: "
                    + names(AccountType.values()), e);
        }
    }

    private static Ownership parseOwnership(String value) {
        try {
            return Ownership.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Titularidad no valida. Valores admitidos: "
                    + names(Ownership.values()), e);
        }
    }

    private static String names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }
}
