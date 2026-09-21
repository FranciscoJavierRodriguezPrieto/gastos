package com.gastos.expenses.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila de {@code fixed_expense}.
 *
 * <p>La vigencia se guarda como el dia 1 del mes: no hay tipo SQL para "mes", y una fecha
 * normalizada al dia 1 es comparable y ordenable sin funciones sobre la columna. La
 * traduccion a {@code YearMonth} la hace {@link FixedExpenseJpaMapper}.</p>
 */
@Entity
@Table(name = "fixed_expense")
public class FixedExpenseEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 140)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    /** Null mientras siga vigente. */
    @Column(name = "end_month")
    private LocalDate endMonth;

    protected FixedExpenseEntity() {
        // Requerido por JPA.
    }

    public FixedExpenseEntity(UUID id, UUID householdId, UUID createdBy, String description,
                              BigDecimal amount, String category, int dayOfMonth, UUID accountId,
                              LocalDate startMonth, LocalDate endMonth) {
        this.id = id;
        this.householdId = householdId;
        this.createdBy = createdBy;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.dayOfMonth = dayOfMonth;
        this.accountId = accountId;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getCreatedBy() {
        return createdBy;
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

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getStartMonth() {
        return startMonth;
    }

    public LocalDate getEndMonth() {
        return endMonth;
    }
}
