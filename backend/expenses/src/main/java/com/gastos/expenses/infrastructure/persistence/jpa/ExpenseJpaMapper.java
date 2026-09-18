package com.gastos.expenses.infrastructure.persistence.jpa;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;

/** Traduce entre la fila de {@code expense} y el agregado del dominio. */
public final class ExpenseJpaMapper {

    private ExpenseJpaMapper() {
    }

    public static ExpenseEntity toEntity(Expense expense) {
        return new ExpenseEntity(
                expense.id().value(),
                expense.householdId().value(),
                expense.registeredBy().value(),
                expense.description(),
                expense.amount().amount(),
                expense.category().name(),
                expense.recurrence().name(),
                expense.incurredOn(),
                expense.accountId());
    }

    public static Expense toDomain(ExpenseEntity entity) {
        return Expense.rehydrate(
                new ExpenseId(entity.getId()),
                new HouseholdId(entity.getHouseholdId()),
                new UserId(entity.getRegisteredBy()),
                entity.getDescription(),
                Money.euros(entity.getAmount()),
                ExpenseCategory.valueOf(entity.getCategory()),
                Recurrence.valueOf(entity.getRecurrence()),
                entity.getIncurredOn(),
                entity.getAccountId());
    }
}
