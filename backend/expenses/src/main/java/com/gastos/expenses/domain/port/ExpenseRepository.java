package com.gastos.expenses.domain.port;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.shared.domain.HouseholdId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** Puerto de salida del contexto de gastos. Siempre acotado por hogar. */
public interface ExpenseRepository {

    Optional<Expense> findById(HouseholdId householdId, ExpenseId expenseId);

    List<Expense> findByMonth(HouseholdId householdId, YearMonth month);

    List<Expense> findRecurringCommitments(HouseholdId householdId);

    Expense save(Expense expense);

    void delete(HouseholdId householdId, ExpenseId expenseId);
}
