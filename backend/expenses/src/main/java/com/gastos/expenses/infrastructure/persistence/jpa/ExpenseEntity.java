package com.gastos.expenses.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila de la tabla {@code expense}.
 *
 * <p>Estructura de datos plana. El agregado {@code Expense} no lleva anotaciones de JPA:
 * la traduccion la hace {@link ExpenseJpaMapper}.</p>
 */
@Entity
@Table(name = "expense")
public class ExpenseEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "registered_by", nullable = false)
    private UUID registeredBy;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 20)
    private String recurrence;

    @Column(name = "incurred_on", nullable = false)
    private LocalDate incurredOn;

    /** Opcional: un gasto puede no estar asociado a ninguna cuenta concreta. */
    @Column(name = "account_id")
    private UUID accountId;

    protected ExpenseEntity() {
        // Requerido por JPA.
    }

    public ExpenseEntity(UUID id, UUID householdId, UUID registeredBy, String description,
                         BigDecimal amount, String category, String recurrence, LocalDate incurredOn,
                         UUID accountId) {
        this.id = id;
        this.householdId = householdId;
        this.registeredBy = registeredBy;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.recurrence = recurrence;
        this.incurredOn = incurredOn;
        this.accountId = accountId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getRegisteredBy() {
        return registeredBy;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public LocalDate getIncurredOn() {
        return incurredOn;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
