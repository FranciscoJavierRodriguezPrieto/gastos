package com.gastos.expenses.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un gasto fijo.
 *
 * @param endMonth null mientras siga vigente; si no, el primer mes que ya no genera
 * @param active   atajo para la pantalla, que no tiene por que interpretar fechas
 */
public record FixedExpenseResponse(UUID id,
                                   String description,
                                   BigDecimal amount,
                                   String category,
                                   String categoryLabel,
                                   int dayOfMonth,
                                   UUID accountId,
                                   String startMonth,
                                   String endMonth,
                                   boolean active) {
}
