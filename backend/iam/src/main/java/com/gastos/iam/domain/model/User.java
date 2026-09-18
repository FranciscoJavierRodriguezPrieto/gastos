package com.gastos.iam.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;

/**
 * Miembro del hogar. El agregado no guarda credenciales en claro ni el hash:
 * la autenticacion vive en el adaptador de seguridad (JWT/Passkeys), de modo que el
 * dominio nunca puede filtrar material sensible por serializacion accidental.
 */
public final class User {

    private final UserId id;
    private final HouseholdId householdId;
    private final Email email;
    private String displayName;
    private final Role role;
    private Money monthlyNetIncome;

    private User(UserId id, HouseholdId householdId, Email email, String displayName, Role role,
                 Money monthlyNetIncome) {
        this.id = Guard.notNull(id, "id");
        this.householdId = Guard.notNull(householdId, "householdId");
        this.email = Guard.notNull(email, "email");
        this.displayName = Guard.notBlank(displayName, "displayName");
        this.role = Guard.notNull(role, "role");
        this.monthlyNetIncome = requireNonNegative(monthlyNetIncome);
    }

    public static User register(HouseholdId householdId, Email email, String displayName, Role role,
                                Money monthlyNetIncome) {
        return new User(UserId.newId(), householdId, email, displayName, role, monthlyNetIncome);
    }

    public static User rehydrate(UserId id, HouseholdId householdId, Email email, String displayName,
                                 Role role, Money monthlyNetIncome) {
        return new User(id, householdId, email, displayName, role, monthlyNetIncome);
    }

    public void updateMonthlyNetIncome(Money newIncome) {
        this.monthlyNetIncome = requireNonNegative(newIncome);
    }

    public void rename(String newDisplayName) {
        this.displayName = Guard.notBlank(newDisplayName, "displayName");
    }

    public boolean belongsTo(HouseholdId candidate) {
        return householdId.equals(candidate);
    }

    private static Money requireNonNegative(Money income) {
        Guard.notNull(income, "monthlyNetIncome");
        if (income.isNegative()) {
            throw new com.gastos.shared.domain.DomainException("Los ingresos netos no pueden ser negativos");
        }
        return income;
    }

    public UserId id() {
        return id;
    }

    public HouseholdId householdId() {
        return householdId;
    }

    public Email email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    public Money monthlyNetIncome() {
        return monthlyNetIncome;
    }
}
