package com.gastos.expenses.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumen del mes tal y como lo consume el dashboard.
 *
 * @param monthlyCommitments carga mensual de compromisos estables; es el valor que la
 *                           herramienta de hipoteca usa como "otras deudas" en el DTI
 */
public record MonthlySummaryResponse(String month,
                                     BigDecimal total,
                                     BigDecimal monthlyCommitments,
                                     List<CategoryAmountResponse> byCategory) {

    /** Una linea del desglose por categoria, con su peso sobre el total. */
    public record CategoryAmountResponse(String category,
                                         String label,
                                         BigDecimal amount,
                                         BigDecimal share) {
    }
}
