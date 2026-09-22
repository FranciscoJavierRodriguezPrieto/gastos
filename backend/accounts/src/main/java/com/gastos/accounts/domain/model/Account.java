package com.gastos.accounts.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Raiz de agregado "Cuenta". Encapsula el saldo y sus transiciones: ningun caso de
 * uso escribe el saldo directamente, solo a traves de operaciones con nombre de
 * negocio. Asi existe una unica fuente de verdad para el balance del hogar.
 */
public final class Account {

    private final AccountId id;
    private final HouseholdId householdId;
    private String alias;
    private final String bankName;
    private final AccountType type;
    private final Ownership ownership;
    private final Set<UserId> holders;
    private Money balance;
    private Instant balanceUpdatedAt;

    private Account(AccountId id, HouseholdId householdId, String alias, String bankName,
                    AccountType type, Ownership ownership, Set<UserId> holders, Money balance,
                    Instant balanceUpdatedAt) {
        this.id = Guard.notNull(id, "id");
        this.householdId = Guard.notNull(householdId, "householdId");
        this.alias = Guard.notBlank(alias, "alias");
        this.bankName = Guard.notBlank(bankName, "bankName");
        this.type = Guard.notNull(type, "type");
        this.ownership = Guard.notNull(ownership, "ownership");
        this.holders = new LinkedHashSet<>(Guard.notEmpty(holders, "holders"));
        this.balance = Guard.notNull(balance, "balance");
        this.balanceUpdatedAt = Guard.notNull(balanceUpdatedAt, "balanceUpdatedAt");
        validateHolders();
    }

    public static Account open(HouseholdId householdId, String alias, String bankName,
                               AccountType type, Ownership ownership, Set<UserId> holders,
                               Money initialBalance, Instant now) {
        return new Account(AccountId.newId(), householdId, alias, bankName, type, ownership,
                holders, initialBalance, now);
    }

    public static Account rehydrate(AccountId id, HouseholdId householdId, String alias, String bankName,
                                    AccountType type, Ownership ownership, Set<UserId> holders,
                                    Money balance, Instant balanceUpdatedAt) {
        return new Account(id, householdId, alias, bankName, type, ownership, holders, balance,
                balanceUpdatedAt);
    }

    private void validateHolders() {
        if (ownership == Ownership.CONJUNTA && holders.size() < 2) {
            throw new DomainException("Una cuenta conjunta requiere al menos dos titulares");
        }
        if (ownership == Ownership.INDIVIDUAL && holders.size() != 1) {
            throw new DomainException("Una cuenta individual debe tener exactamente un titular");
        }
    }

    /** Registra un ingreso en la cuenta. */
    public void credit(Money amount, Instant now) {
        requirePositive(amount);
        this.balance = balance.plus(amount);
        this.balanceUpdatedAt = Guard.notNull(now, "now");
    }

    /** Registra un cargo. Solo las tarjetas de credito pueden quedar en negativo. */
    public void debit(Money amount, Instant now) {
        requirePositive(amount);
        Money newBalance = balance.minus(amount);
        if (newBalance.isNegative() && type != AccountType.TARJETA_CREDITO) {
            throw new DomainException("El cargo dejaria la cuenta en descubierto: " + alias);
        }
        this.balance = newBalance;
        this.balanceUpdatedAt = Guard.notNull(now, "now");
    }

    /** Conciliacion manual contra el extracto bancario. */
    public void reconcileTo(Money realBalance, Instant now) {
        this.balance = Guard.notNull(realBalance, "realBalance");
        this.balanceUpdatedAt = Guard.notNull(now, "now");
    }

    public void rename(String newAlias) {
        this.alias = Guard.notBlank(newAlias, "alias");
    }

    /** Comprobacion de autorizacion a nivel de objeto: barrera frente a BOLA/IDOR. */
    public boolean isAccessibleBy(HouseholdId requesterHousehold) {
        return householdId.equals(requesterHousehold);
    }

    public boolean isHeldBy(UserId userId) {
        return holders.contains(userId);
    }

    private static void requirePositive(Money amount) {
        Guard.notNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new DomainException("El importe de la operacion debe ser mayor que cero");
        }
    }

    public AccountId id() {
        return id;
    }

    public HouseholdId householdId() {
        return householdId;
    }

    public String alias() {
        return alias;
    }

    public String bankName() {
        return bankName;
    }

    public AccountType type() {
        return type;
    }

    public Ownership ownership() {
        return ownership;
    }

    public Set<UserId> holders() {
        return Set.copyOf(holders);
    }

    public Money balance() {
        return balance;
    }

    public Instant balanceUpdatedAt() {
        return balanceUpdatedAt;
    }
}
