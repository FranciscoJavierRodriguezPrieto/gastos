package com.gastos.accounts.infrastructure.persistence.memory;

import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountId;
import com.gastos.accounts.domain.port.AccountRepository;
import com.gastos.shared.domain.HouseholdId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia en memoria.
 *
 * <p><strong>Provisional</strong> hasta {@code feature/persistence-postgresql}. Los
 * datos se pierden al reiniciar.</p>
 */
@Repository
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<AccountId, Account> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findById(HouseholdId householdId, AccountId accountId) {
        return Optional.ofNullable(store.get(accountId))
                .filter(account -> account.isAccessibleBy(householdId));
    }

    @Override
    public List<Account> findAllByHousehold(HouseholdId householdId) {
        return store.values().stream()
                .filter(account -> account.isAccessibleBy(householdId))
                .sorted(Comparator.comparing(Account::alias))
                .toList();
    }

    @Override
    public Account save(Account account) {
        store.put(account.id(), account);
        return account;
    }

    @Override
    public void delete(HouseholdId householdId, AccountId accountId) {
        findById(householdId, accountId).ifPresent(account -> store.remove(account.id()));
    }
}
