package com.gastos.expenses.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Money;
import java.math.BigDecimal;

/**
 * Periodicidad de un gasto. {@link #toMonthlyEquivalent(Money)} normaliza cualquier
 * periodicidad a coste mensual para que el dashboard y el DTI comparen magnitudes
 * homogeneas (un seguro anual de 600 EUR pesa 50 EUR/mes, no 600).
 */
public enum Recurrence {

    PUNTUAL(BigDecimal.ZERO),
    MENSUAL(BigDecimal.ONE),
    BIMESTRAL(new BigDecimal("0.5")),
    TRIMESTRAL(new BigDecimal("0.333333")),
    SEMESTRAL(new BigDecimal("0.166667")),
    ANUAL(new BigDecimal("0.083333"));

    private final BigDecimal monthlyFactor;

    Recurrence(BigDecimal monthlyFactor) {
        this.monthlyFactor = monthlyFactor;
    }

    /**
     * Coste mensual equivalente. Un gasto PUNTUAL no genera compromiso recurrente y
     * por tanto aporta cero al calculo de carga mensual.
     */
    public Money toMonthlyEquivalent(Money amount) {
        if (amount == null) {
            throw new DomainException("El importe es obligatorio para normalizar la recurrencia");
        }
        return amount.multipliedBy(monthlyFactor);
    }

    public boolean isRecurring() {
        return this != PUNTUAL;
    }
}
