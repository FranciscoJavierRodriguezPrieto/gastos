package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/**
 * Estructura financiera de la operacion: cuanto se pide prestado, cuanto se aporta de
 * ahorro y si el ahorro disponible llega a cubrir la entrada mas los gastos.
 *
 * <p>Reglas del reparto del ahorro, en este orden:</p>
 * <ol>
 *   <li>se reserva el fondo de emergencia que el hogar no quiere tocar;</li>
 *   <li>se pagan los gastos de compraventa, que nunca son financiables;</li>
 *   <li>lo que sobre va a la entrada, reduciendo el prestamo;</li>
 *   <li>la entrada nunca baja del minimo que impone el LTV maximo aplicable.</li>
 * </ol>
 *
 * <p>El fondo de emergencia es un objetivo, no un requisito: si el hogar puede firmar
 * pero quedandose sin colchon, la operacion sigue siendo posible y el analisis de
 * viabilidad lo senala como advertencia.</p>
 */
public record FinancingPlan(Money propertyPrice,
                            Money availableSavings,
                            Money targetReserve,
                            UpfrontCosts upfrontCosts,
                            Percentage maxLoanToValue,
                            Money loanAmount,
                            Money downPayment,
                            Money cashRequired,
                            Money savingsBuffer,
                            boolean savingsSufficient) {

    public FinancingPlan {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(availableSavings, "availableSavings");
        Guard.notNull(targetReserve, "targetReserve");
        Guard.notNull(upfrontCosts, "upfrontCosts");
        Guard.notNull(maxLoanToValue, "maxLoanToValue");
        Guard.notNull(loanAmount, "loanAmount");
        Guard.notNull(downPayment, "downPayment");
        Guard.notNull(cashRequired, "cashRequired");
        Guard.notNull(savingsBuffer, "savingsBuffer");
    }

    public static FinancingPlan compute(Money propertyPrice, Money availableSavings, Money targetReserve,
                                        UpfrontCosts upfrontCosts, Percentage maxLoanToValue) {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(availableSavings, "availableSavings");
        Guard.notNull(targetReserve, "targetReserve");
        Guard.notNull(upfrontCosts, "upfrontCosts");
        Guard.notNull(maxLoanToValue, "maxLoanToValue");

        Money maxLoan = propertyPrice.percentageOf(maxLoanToValue);
        Money minimumDownPayment = propertyPrice.minus(maxLoan);
        Money minimumCash = minimumDownPayment.plus(upfrontCosts.total());
        boolean sufficient = !availableSavings.isLessThan(minimumCash);

        Money savingsForDownPayment = availableSavings
                .minus(targetReserve)
                .minus(upfrontCosts.total());
        Money downPayment = floorAtZero(savingsForDownPayment);
        if (downPayment.isGreaterThan(propertyPrice)) {
            downPayment = propertyPrice;
        }
        if (downPayment.isLessThan(minimumDownPayment)) {
            downPayment = minimumDownPayment;
        }

        Money loanAmount = propertyPrice.minus(downPayment);
        Money cashRequired = downPayment.plus(upfrontCosts.total());
        Money buffer = availableSavings.minus(cashRequired);

        return new FinancingPlan(propertyPrice, availableSavings, targetReserve, upfrontCosts,
                maxLoanToValue, loanAmount, downPayment, cashRequired, buffer, sufficient);
    }

    /** LTV real de la operacion resultante. */
    public Percentage effectiveLoanToValue() {
        return Percentage.fromRate(loanAmount.ratioTo(propertyPrice));
    }

    /** Dinero que falta para poder firmar. Cero si el ahorro es suficiente. */
    public Money shortfall() {
        return savingsSufficient ? Money.zero() : savingsBuffer.abs();
    }

    private static Money floorAtZero(Money amount) {
        return amount.isNegative() ? Money.zero() : amount;
    }
}
