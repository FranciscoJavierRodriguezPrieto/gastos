package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;

/** Una cuota del cuadro de amortizacion. */
public record Installment(int number,
                          Money payment,
                          Money interest,
                          Money principalPaid,
                          Money outstandingBalance) {

    public Installment {
        Guard.positive(number, "number");
        Guard.notNull(payment, "payment");
        Guard.notNull(interest, "interest");
        Guard.notNull(principalPaid, "principalPaid");
        Guard.notNull(outstandingBalance, "outstandingBalance");
    }
}
