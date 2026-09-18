package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import java.util.List;

/** Cuadro de amortizacion completo mas sus totales. */
public record AmortizationSchedule(LoanTerms terms,
                                   Money monthlyPayment,
                                   Money totalInterest,
                                   List<Installment> installments) {

    public AmortizationSchedule {
        Guard.notNull(terms, "terms");
        Guard.notNull(monthlyPayment, "monthlyPayment");
        Guard.notNull(totalInterest, "totalInterest");
        installments = List.copyOf(Guard.notEmpty(installments, "installments"));
    }

    /** Coste total del credito: capital devuelto mas intereses. */
    public Money totalRepaid() {
        return terms.principal().plus(totalInterest);
    }

    public Installment installment(int number) {
        Guard.inRange(number, 1, installments.size(), "number");
        return installments.get(number - 1);
    }
}
