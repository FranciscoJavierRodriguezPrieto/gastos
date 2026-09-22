package com.gastos.mortgage.domain.service;

import com.gastos.mortgage.domain.model.AmortizationSchedule;
import com.gastos.mortgage.domain.model.Installment;
import com.gastos.mortgage.domain.model.LoanTerms;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de amortizacion frances (cuota constante), el usado por practicamente
 * todas las hipotecas espanolas.
 *
 * <pre>
 *            P * i * (1 + i)^n
 *   cuota = ---------------------
 *             (1 + i)^n - 1
 * </pre>
 *
 * <p>donde {@code i} es el tipo nominal mensual (TIN/12, criterio de la banca
 * espanola) y {@code n} el numero de cuotas. Los pasos intermedios se calculan con
 * precision extendida y solo el resultado se redondea a dos decimales; redondear
 * antes acumularia varios euros de desviacion a lo largo de 30 anos.</p>
 *
 * <p>Clase sin estado y sin dependencias de framework: es logica de dominio pura y
 * por tanto testeable de forma exhaustiva.</p>
 */
public final class AmortizationCalculator {

    private AmortizationCalculator() {
    }

    /** Cuota mensual constante del prestamo. */
    public static Money monthlyPayment(LoanTerms terms) {
        Guard.notNull(terms, "terms");
        BigDecimal monthlyRate = terms.annualNominalRate().asMonthlyRate();
        int months = terms.termMonths();

        if (monthlyRate.signum() == 0) {
            return terms.principal().dividedBy(BigDecimal.valueOf(months));
        }

        BigDecimal onePlusRatePowN = BigDecimal.ONE.add(monthlyRate)
                .pow(months, Money.CALCULATION_CONTEXT);
        BigDecimal numerator = monthlyRate.multiply(onePlusRatePowN, Money.CALCULATION_CONTEXT);
        BigDecimal denominator = onePlusRatePowN.subtract(BigDecimal.ONE);

        return terms.principal()
                .multipliedBy(numerator.divide(denominator, Money.CALCULATION_CONTEXT));
    }

    /**
     * Cuadro de amortizacion completo. La ultima cuota absorbe el redondeo acumulado
     * para que el capital pendiente cierre exactamente en cero.
     */
    public static AmortizationSchedule schedule(LoanTerms terms) {
        Guard.notNull(terms, "terms");
        Money payment = monthlyPayment(terms);
        BigDecimal monthlyRate = terms.annualNominalRate().asMonthlyRate();

        List<Installment> installments = new ArrayList<>(terms.termMonths());
        Money outstanding = terms.principal();
        Money totalInterest = Money.zero();

        for (int number = 1; number <= terms.termMonths(); number++) {
            Money interest = outstanding.multipliedBy(monthlyRate);
            boolean isLast = number == terms.termMonths();
            Money principalPaid = isLast ? outstanding : payment.minus(interest);
            Money actualPayment = isLast ? outstanding.plus(interest) : payment;

            outstanding = outstanding.minus(principalPaid);
            totalInterest = totalInterest.plus(interest);
            installments.add(new Installment(number, actualPayment, interest, principalPaid, outstanding));
        }

        return new AmortizationSchedule(terms, payment, totalInterest, installments);
    }
}
