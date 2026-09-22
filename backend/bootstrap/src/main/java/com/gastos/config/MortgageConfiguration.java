package com.gastos.config;

import com.gastos.mortgage.domain.policy.LendingPolicy;
import com.gastos.mortgage.domain.policy.PurchaseCostsPolicy;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Raiz de composicion del contexto de hipoteca.
 *
 * <p>Las anotaciones de Spring viven aqui y no en el dominio: el nucleo financiero
 * no conoce el framework, lo que permite testearlo sin contexto de aplicacion y
 * sustituir Spring sin tocar una sola regla de negocio.</p>
 */
@Configuration
@EnableConfigurationProperties(MortgageProperties.class)
public class MortgageConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public PurchaseCostsPolicy purchaseCostsPolicy(MortgageProperties properties) {
        MortgageProperties.PurchaseCosts costes = properties.purchaseCosts();
        return new PurchaseCostsPolicy(
                Percentage.of(costes.transferTaxRate()),
                Percentage.of(costes.ancillaryCostsRate()),
                Percentage.of(costes.habitualResidenceRebate()),
                Money.euros(costes.habitualResidenceRebateLimit()),
                Percentage.of(costes.largeFamilyRate()));
    }

    @Bean
    public LendingPolicy lendingPolicy(MortgageProperties properties) {
        MortgageProperties.Lending criterio = properties.lending();
        return new LendingPolicy(
                Percentage.of(criterio.optimalHousingDti()),
                Percentage.of(criterio.maxHousingDti()),
                Percentage.of(criterio.optimalTotalDti()),
                Percentage.of(criterio.maxTotalDti()),
                Money.euros(criterio.minResidualIncome()),
                Percentage.of(criterio.standardLoanToValue()));
    }

    @Bean
    public MortgageSimulator mortgageSimulator(PurchaseCostsPolicy purchaseCostsPolicy,
                                               LendingPolicy lendingPolicy) {
        return new MortgageSimulator(purchaseCostsPolicy, lendingPolicy);
    }
}
