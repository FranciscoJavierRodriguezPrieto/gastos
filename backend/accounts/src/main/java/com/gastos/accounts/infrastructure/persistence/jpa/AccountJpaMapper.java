package com.gastos.accounts.infrastructure.persistence.jpa;

import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountId;
import com.gastos.accounts.domain.model.AccountType;
import com.gastos.accounts.domain.model.Ownership;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Traduce entre la fila de {@code account} y el agregado del dominio. */
public final class AccountJpaMapper {

    private AccountJpaMapper() {
    }

    public static AccountEntity toEntity(Account account) {
        return new AccountEntity(
                account.id().value(),
                account.householdId().value(),
                account.alias(),
                account.bankName(),
                account.type().name(),
                account.ownership().name(),
                account.balance().amount(),
                account.balanceUpdatedAt(),
                account.holders().stream().map(UserId::value)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * Reconstruye el agregado desde la fila.
     *
     * <p>Se usa {@code rehydrate} y no el constructor de alta: recuperar algo que ya
     * existe no es crearlo, y confundirlos generaria un identificador nuevo en cada
     * lectura.</p>
     */
    public static Account toDomain(AccountEntity entity) {
        Set<UserId> holders = entity.getHolders().stream()
                .map(UserId::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return Account.rehydrate(
                new AccountId(entity.getId()),
                new HouseholdId(entity.getHouseholdId()),
                entity.getAlias(),
                entity.getBankName(),
                AccountType.valueOf(entity.getType()),
                Ownership.valueOf(entity.getOwnership()),
                holders,
                Money.euros(entity.getBalance()),
                entity.getBalanceUpdatedAt());
    }
}
