package com.gastos.config;

import com.gastos.mortgage.domain.policy.LendingPolicy;
import com.gastos.mortgage.domain.policy.PurchaseCostsPolicy;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import java.time.Clock;
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
public class MortgageConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public PurchaseCostsPolicy purchaseCostsPolicy() {
        return PurchaseCostsPolicy.madridSecondHand();
    }

    @Bean
    public LendingPolicy lendingPolicy() {
        return LendingPolicy.spanishStandard();
    }

    @Bean
    public MortgageSimulator mortgageSimulator(PurchaseCostsPolicy purchaseCostsPolicy,
                                               LendingPolicy lendingPolicy) {
        return new MortgageSimulator(purchaseCostsPolicy, lendingPolicy);
    }
}
