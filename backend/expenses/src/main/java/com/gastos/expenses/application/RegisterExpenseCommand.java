package com.gastos.expenses.application;

import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orden de alta de un gasto, expresada ya en tipos de dominio.
 *
 * <p>Separar el comando del DTO de entrada evita que el contrato HTTP condicione al
 * modelo: el adaptador REST traduce cadenas y numeros sueltos a {@code Money},
 * {@code ExpenseCategory} y {@code Recurrence}, y la capa de aplicacion solo trabaja
 * con conceptos de negocio.</p>
 */
public record RegisterExpenseCommand(HouseholdId householdId,
                                     UserId registeredBy,
                                     String description,
                                     Money amount,
                                     ExpenseCategory category,
                                     Recurrence recurrence,
                                     LocalDate incurredOn,
                                     UUID accountId) {
}
