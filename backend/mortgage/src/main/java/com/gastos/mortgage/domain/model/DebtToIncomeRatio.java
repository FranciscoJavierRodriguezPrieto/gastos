package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/**
 * Ratio de endeudamiento (DTI) del hogar.
 *
 * <ul>
 *   <li><strong>DTI vivienda</strong>: cuota hipotecaria sobre ingresos netos. Es el
 *       ratio que la banca espanola limita en torno al 30%.</li>
 *   <li><strong>DTI total</strong>: cuota hipotecaria mas el resto de deudas estables
 *       (prestamo del coche, financiaciones) sobre los mismos ingresos.</li>
 * </ul>
 *
 * <p>Se calcula sobre ingresos NETOS mensuales del hogar, incluyendo las pagas extra
 * prorrateadas si el usuario las declara asi.</p>
 */
public record DebtToIncomeRatio(Money netMonthlyIncome,
                                Money housingPayment,
                                Money otherMonthlyDebts) {

    public DebtToIncomeRatio {
        Guard.notNull(netMonthlyIncome, "netMonthlyIncome");
        Guard.notNull(housingPayment, "housingPayment");
        Guard.notNull(otherMonthlyDebts, "otherMonthlyDebts");
        if (!netMonthlyIncome.isPositive()) {
            throw new DomainException("Los ingresos netos mensuales deben ser mayores que cero");
        }
        if (housingPayment.isNegative() || otherMonthlyDebts.isNegative()) {
            throw new DomainException("Las cuotas y deudas no pueden ser negativas");
        }
    }

    public Percentage housingRatio() {
        return Percentage.fromRate(housingPayment.ratioTo(netMonthlyIncome));
    }

    public Percentage totalRatio() {
        return Percentage.fromRate(totalDebtPayments().ratioTo(netMonthlyIncome));
    }

    public Money totalDebtPayments() {
        return housingPayment.plus(otherMonthlyDebts);
    }

    /** Renta disponible tras atender todas las cuotas: el colchon real del hogar. */
    public Money residualIncome() {
        return netMonthlyIncome.minus(totalDebtPayments());
    }
}
