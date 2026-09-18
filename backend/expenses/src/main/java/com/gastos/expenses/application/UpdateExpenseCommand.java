package com.gastos.expenses.application;

import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.shared.domain.Money;
import java.time.LocalDate;

/** Orden de modificacion de un gasto ya registrado. */
public record UpdateExpenseCommand(String description,
                                   Money amount,
                                   ExpenseCategory category,
                                   Recurrence recurrence,
                                   LocalDate incurredOn) {
}
