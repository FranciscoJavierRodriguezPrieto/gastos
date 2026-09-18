package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/**
 * Condiciones del prestamo hipotecario: capital, tipo nominal anual (TIN) y plazo.
 *
 * <p>El plazo se acota entre 5 y 40 anos porque fuera de ese rango ninguna entidad
 * espanola concede financiacion y el simulador daria resultados enganosos.</p>
 */
public record LoanTerms(Money principal, Percentage annualNominalRate, int termYears) {

    public static final int MIN_TERM_YEARS = 5;
    public static final int MAX_TERM_YEARS = 40;

    public LoanTerms {
        Guard.notNull(principal, "principal");
        Guard.notNull(annualNominalRate, "annualNominalRate");
        Guard.inRange(termYears, MIN_TERM_YEARS, MAX_TERM_YEARS, "termYears");
        if (!principal.isPositive()) {
            throw new DomainException("El capital del prestamo debe ser mayor que cero");
        }
        if (annualNominalRate.value().signum() < 0) {
            throw new DomainException("El tipo de interes no puede ser negativo");
        }
    }

    public int termMonths() {
        return termYears * 12;
    }
}
