package com.gastos.expenses.infrastructure.persistence.jpa;

import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.model.FixedExpenseId;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.LocalDate;
import java.time.YearMonth;

/** Traduce entre la fila de {@code fixed_expense} y el agregado del dominio. */
public final class FixedExpenseJpaMapper {

    private FixedExpenseJpaMapper() {
    }

    /** Un mes se guarda como su dia 1. */
    public static LocalDate toColumn(YearMonth month) {
        return month == null ? null : month.atDay(1);
    }

    public static YearMonth toMonth(LocalDate column) {
        return column == null ? null : YearMonth.from(column);
    }

    public static FixedExpenseEntity toEntity(FixedExpense fixedExpense) {
        return new FixedExpenseEntity(
                fixedExpense.id().value(),
                fixedExpense.householdId().value(),
                fixedExpense.createdBy().value(),
                fixedExpense.description(),
                fixedExpense.amount().amount(),
                fixedExpense.category().name(),
                fixedExpense.dayOfMonth(),
                fixedExpense.accountId(),
                toColumn(fixedExpense.startMonth()),
                toColumn(fixedExpense.endMonth()));
    }

    public static FixedExpense toDomain(FixedExpenseEntity entity) {
        return FixedExpense.rehydrate(
                new FixedExpenseId(entity.getId()),
                new HouseholdId(entity.getHouseholdId()),
                new UserId(entity.getCreatedBy()),
                entity.getDescription(),
                Money.euros(entity.getAmount()),
                ExpenseCategory.valueOf(entity.getCategory()),
                entity.getDayOfMonth(),
                entity.getAccountId(),
                toMonth(entity.getStartMonth()),
                toMonth(entity.getEndMonth()));
    }
}
