package com.gastos.expenses.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Vista de lectura calculada del mes: total gastado, desglose por categoria y carga
 * de compromisos recurrentes.
 *
 * <p>Es el nexo entre el contexto de gastos y el de hipoteca: {@link #monthlyCommitments()}
 * alimenta el DTI total sin que el contexto de hipoteca conozca el agregado Expense.</p>
 */
public record MonthlySpendingReport(YearMonth month,
                                    Money total,
                                    Money monthlyCommitments,
                                    Map<ExpenseCategory, Money> byCategory) {

    public MonthlySpendingReport {
        Guard.notNull(month, "month");
        Guard.notNull(total, "total");
        Guard.notNull(monthlyCommitments, "monthlyCommitments");
        byCategory = Map.copyOf(Guard.notNull(byCategory, "byCategory"));
    }

    public static MonthlySpendingReport of(YearMonth month, List<Expense> expenses) {
        Guard.notNull(month, "month");
        Guard.notNull(expenses, "expenses");

        Map<ExpenseCategory, Money> breakdown = new EnumMap<>(ExpenseCategory.class);
        Money total = Money.zero();
        Money commitments = Money.zero();

        for (Expense expense : expenses) {
            if (!expense.belongsTo(month)) {
                continue;
            }
            total = total.plus(expense.amount());
            breakdown.merge(expense.category(), expense.amount(), Money::plus);
            if (expense.isStableCommitment()) {
                commitments = commitments.plus(expense.monthlyEquivalent());
            }
        }
        return new MonthlySpendingReport(month, total, commitments, breakdown);
    }

    public Money amountFor(ExpenseCategory category) {
        return byCategory.getOrDefault(Guard.notNull(category, "category"), Money.zero());
    }

    /** Superavit del mes: KPI principal del dashboard. */
    public Money surplusAgainst(Money monthlyIncome) {
        return Guard.notNull(monthlyIncome, "monthlyIncome").minus(total);
    }
}
