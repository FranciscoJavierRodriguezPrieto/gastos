package com.gastos.mortgage.domain.policy;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/**
 * Umbrales de riesgo con los que se juzga la operacion. Reproducen el criterio
 * habitual de la banca espanola y del Banco de Espana, pero son configurables porque
 * cada entidad afina sus limites.
 *
 * @param optimalHousingDti DTI vivienda por debajo del cual la operacion es holgada
 * @param maxHousingDti     DTI vivienda maximo admisible
 * @param optimalTotalDti   DTI total por debajo del cual la operacion es holgada
 * @param maxTotalDti       DTI total maximo admisible
 * @param minResidualIncome renta disponible minima tras pagar todas las cuotas
 * @param standardLoanToValue LTV que concede la banca sin ayuda publica; es el suelo
 *                            sobre el que un programa tiene que mejorar para aplicarse
 */
public record LendingPolicy(Percentage optimalHousingDti,
                            Percentage maxHousingDti,
                            Percentage optimalTotalDti,
                            Percentage maxTotalDti,
                            Money minResidualIncome,
                            Percentage standardLoanToValue) {

    public LendingPolicy {
        Guard.notNull(optimalHousingDti, "optimalHousingDti");
        Guard.notNull(maxHousingDti, "maxHousingDti");
        Guard.notNull(optimalTotalDti, "optimalTotalDti");
        Guard.notNull(maxTotalDti, "maxTotalDti");
        Guard.notNull(minResidualIncome, "minResidualIncome");
        Guard.notNull(standardLoanToValue, "standardLoanToValue");
    }

    /**
     * Criterio de referencia: 30% de DTI vivienda y 40% de DTI total como techos,
     * con 25% y 35% como zona comoda, 1.000 EUR de renta disponible minima para un
     * hogar de dos personas, y el clasico 80% de LTV sin ayuda publica.
     */
    public static LendingPolicy spanishStandard() {
        return new LendingPolicy(
                Percentage.of("25.00"),
                Percentage.of("30.00"),
                Percentage.of("35.00"),
                Percentage.of("40.00"),
                Money.euros(1_000),
                Percentage.of("80.00"));
    }
}
