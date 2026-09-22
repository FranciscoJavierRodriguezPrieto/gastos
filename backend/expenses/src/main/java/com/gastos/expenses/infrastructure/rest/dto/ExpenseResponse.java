package com.gastos.expenses.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representacion de salida de un gasto.
 *
 * <p>No expone {@code householdId} ni {@code registeredBy}: el cliente ya sabe de que
 * hogar es, y el resto es informacion interna que no necesita cruzar la frontera.</p>
 *
 * @param monthlyEquivalent coste mensual prorrateado, calculado en el dominio para que
 *                          el cliente no tenga que replicar la formula
 * @param stableCommitment  si la banca lo computaria como deuda estable en el DTI
 * @param fixedExpenseId    la plantilla de gasto fijo que lo genero, o null si se
 *                          registro a mano. La pantalla lo usa para marcarlo y para
 *                          poder llevar a editar el fijo del que sale
 */
public record ExpenseResponse(UUID id,
                              String description,
                              BigDecimal amount,
                              String category,
                              String categoryLabel,
                              String recurrence,
                              LocalDate incurredOn,
                              UUID accountId,
                              BigDecimal monthlyEquivalent,
                              boolean stableCommitment,
                              UUID fixedExpenseId) {
}
