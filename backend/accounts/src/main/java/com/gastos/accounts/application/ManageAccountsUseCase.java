package com.gastos.accounts.application;

import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountId;
import com.gastos.accounts.domain.port.AccountRepository;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;

/**
 * Casos de uso del contexto de cuentas.
 *
 * <p>El {@link Clock} se inyecta en lugar de usar {@code Instant.now()}: sin esto, un
 * test que compruebe la fecha de actualizacion del saldo seria imposible de escribir
 * de forma determinista. La regla esta verificada por ArchUnit.</p>
 */
public class ManageAccountsUseCase {

    private final AccountRepository repository;
    private final Clock clock;

    public ManageAccountsUseCase(AccountRepository repository, Clock clock) {
        this.repository = Guard.notNull(repository, "repository");
        this.clock = Guard.notNull(clock, "clock");
    }

    public Account open(OpenAccountCommand command) {
        Guard.notNull(command, "command");
        Account account = Account.open(
                command.householdId(),
                command.alias(),
                command.bankName(),
                command.type(),
                command.ownership(),
                command.holders(),
                command.initialBalance(),
                clock.instant());
        return repository.save(account);
    }

    public List<Account> listByHousehold(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return repository.findAllByHousehold(householdId);
    }

    public Account findById(HouseholdId householdId, AccountId accountId) {
        return requireOwned(householdId, accountId);
    }

    public Account rename(HouseholdId householdId, AccountId accountId, String newAlias) {
        Account account = requireOwned(householdId, accountId);
        account.rename(newAlias);
        return repository.save(account);
    }

    /** Ajusta el saldo al del extracto bancario. */
    public Account reconcile(HouseholdId householdId, AccountId accountId, Money realBalance) {
        Account account = requireOwned(householdId, accountId);
        account.reconcileTo(realBalance, clock.instant());
        return repository.save(account);
    }

    public Account credit(HouseholdId householdId, AccountId accountId, Money amount) {
        Account account = requireOwned(householdId, accountId);
        account.credit(amount, clock.instant());
        return repository.save(account);
    }

    public Account debit(HouseholdId householdId, AccountId accountId, Money amount) {
        Account account = requireOwned(householdId, accountId);
        account.debit(amount, clock.instant());
        return repository.save(account);
    }

    /** Patrimonio agregado del hogar: la cifra de cabecera del resumen. */
    public Money totalBalance(HouseholdId householdId) {
        return listByHousehold(householdId).stream()
                .map(Account::balance)
                .reduce(Money.zero(), Money::plus);
    }

    public void close(HouseholdId householdId, AccountId accountId) {
        requireOwned(householdId, accountId);
        repository.delete(householdId, accountId);
    }

    private Account requireOwned(HouseholdId householdId, AccountId accountId) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(accountId, "accountId");
        Account account = repository.findById(householdId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada"));
        if (!account.isAccessibleBy(householdId)) {
            throw new ResourceNotFoundException("Cuenta no encontrada");
        }
        return account;
    }
}
