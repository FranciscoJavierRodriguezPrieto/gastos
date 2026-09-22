package com.gastos.expenses.application;

import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.shared.domain.Money;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Alta o modificacion de un gasto fijo, ya en tipos de dominio.
 *
 * @param dayOfMonth dia de cargo, entre 1 y 28
 * @param startMonth desde que mes se genera. Solo se usa al dar de alta: cambiarlo
 *                   despues reescribiria el pasado, que es justo lo que este diseno
 *                   evita
 */
public record FixedExpenseCommand(String description,
                                  Money amount,
                                  ExpenseCategory category,
                                  int dayOfMonth,
                                  UUID accountId,
                                  YearMonth startMonth) {
}
