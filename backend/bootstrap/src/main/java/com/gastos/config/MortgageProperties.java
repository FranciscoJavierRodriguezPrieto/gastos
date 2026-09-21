package com.gastos.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Baremos de la hipoteca, leidos de {@code application.yml}.
 *
 * <p>Hasta ahora el fichero de configuracion traia estos valores con un comentario que
 * decia "cambian por normativa, no por codigo", pero nada los leia: el simulador usaba
 * cifras fijas en el codigo. Cambiar el ITP en el yml no cambiaba nada. Esta clase es la
 * que hace cierto aquel comentario.</p>
 *
 * <p>Los valores ausentes caen en los de Madrid, para que un fichero incompleto no deje la
 * aplicacion sin arrancar.</p>
 */
@ConfigurationProperties(prefix = "gastos.mortgage")
public record MortgageProperties(PurchaseCosts purchaseCosts, Lending lending) {

    public MortgageProperties {
        purchaseCosts = purchaseCosts == null ? new PurchaseCosts(null, null, null, null, null)
                : purchaseCosts;
        lending = lending == null ? new Lending(null, null, null, null, null, null) : lending;
    }

    /**
     * @param habitualResidenceRebate      % de la cuota que se bonifica en vivienda habitual
     * @param habitualResidenceRebateLimit precio maximo para esa bonificacion
     * @param largeFamilyRate              tipo reducido para familia numerosa
     */
    public record PurchaseCosts(BigDecimal transferTaxRate,
                                BigDecimal ancillaryCostsRate,
                                BigDecimal habitualResidenceRebate,
                                BigDecimal habitualResidenceRebateLimit,
                                BigDecimal largeFamilyRate) {

        public PurchaseCosts {
            transferTaxRate = orDefault(transferTaxRate, "6.00");
            ancillaryCostsRate = orDefault(ancillaryCostsRate, "4.00");
            habitualResidenceRebate = orDefault(habitualResidenceRebate, "10.00");
            habitualResidenceRebateLimit = orDefault(habitualResidenceRebateLimit, "250000");
            largeFamilyRate = orDefault(largeFamilyRate, "4.00");
        }
    }

    /** Criterio bancario, no norma legal: ningun limite de DTI es obligatorio en Espana. */
    public record Lending(BigDecimal optimalHousingDti,
                          BigDecimal maxHousingDti,
                          BigDecimal optimalTotalDti,
                          BigDecimal maxTotalDti,
                          BigDecimal minResidualIncome,
                          BigDecimal standardLoanToValue) {

        public Lending {
            optimalHousingDti = orDefault(optimalHousingDti, "25.00");
            maxHousingDti = orDefault(maxHousingDti, "30.00");
            optimalTotalDti = orDefault(optimalTotalDti, "35.00");
            maxTotalDti = orDefault(maxTotalDti, "40.00");
            minResidualIncome = orDefault(minResidualIncome, "1000");
            standardLoanToValue = orDefault(standardLoanToValue, "80.00");
        }
    }

    private static BigDecimal orDefault(BigDecimal value, String fallback) {
        return value == null ? new BigDecimal(fallback) : value;
    }
}
