package com.gastos.mortgage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.gastos.mortgage.domain.model.FinancingPlan;
import com.gastos.mortgage.domain.model.UpfrontCosts;
import com.gastos.mortgage.domain.policy.PurchaseCostsPolicy;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gastos iniciales y estructura de financiacion")
class FinancingPlanTest {

    private static final Percentage LTV_MI_PRIMERA_VIVIENDA = Percentage.of("95.00");

    @Test
    @DisplayName("el 10% no financiable se desglosa en ITP y gastos de formalizacion")
    void splitsUpfrontCosts() {
        UpfrontCosts costs = UpfrontCosts.of(Money.euros(280_000), PurchaseCostsPolicy.madridSecondHand());

        assertThat(costs.transferTax()).isEqualTo(Money.euros(16_800));
        assertThat(costs.ancillaryCosts()).isEqualTo(Money.euros(11_200));
        assertThat(costs.total()).isEqualTo(Money.euros(28_000));
    }

    @Test
    @DisplayName("la bonificacion autonomica para menores de 40 rebaja el ITP al 5,4%")
    void appliesYoungBuyerRebate() {
        UpfrontCosts costs = UpfrontCosts.of(Money.euros(280_000),
                PurchaseCostsPolicy.madridSecondHandYoungBuyer());

        assertThat(costs.transferTax()).isEqualTo(Money.euros(15_120));
        assertThat(costs.total()).isEqualTo(Money.euros(26_320));
    }

    @Test
    @DisplayName("marca el ahorro como insuficiente cuando no cubre entrada mas gastos")
    void detectsInsufficientSavings() {
        Money price = Money.euros(280_000);
        UpfrontCosts costs = UpfrontCosts.of(price, PurchaseCostsPolicy.madridSecondHand());

        FinancingPlan plan = FinancingPlan.compute(price, Money.euros(10_000), Money.zero(), costs,
                LTV_MI_PRIMERA_VIVIENDA);

        // Minimo exigible: 14.000 de entrada (5%) + 28.000 de gastos = 42.000.
        assertThat(plan.savingsSufficient()).isFalse();
        assertThat(plan.shortfall()).isEqualTo(Money.euros(32_000));
    }

    @Test
    @DisplayName("el ahorro sobrante reduce el prestamo tras reservar el fondo de emergencia")
    void extraSavingsReduceTheLoan() {
        Money price = Money.euros(200_000);
        UpfrontCosts costs = UpfrontCosts.of(price, PurchaseCostsPolicy.madridSecondHand());

        FinancingPlan plan = FinancingPlan.compute(price, Money.euros(70_000), Money.euros(6_000), costs,
                LTV_MI_PRIMERA_VIVIENDA);

        // 70.000 - 6.000 de reserva - 20.000 de gastos = 44.000 de entrada.
        assertThat(plan.downPayment()).isEqualTo(Money.euros(44_000));
        assertThat(plan.loanAmount()).isEqualTo(Money.euros(156_000));
        assertThat(plan.cashRequired()).isEqualTo(Money.euros(64_000));
        assertThat(plan.savingsBuffer()).isEqualTo(Money.euros(6_000));
        assertThat(plan.savingsSufficient()).isTrue();
    }

    @Test
    @DisplayName("la entrada nunca baja del minimo que impone el LTV maximo")
    void downPaymentNeverBelowLtvFloor() {
        Money price = Money.euros(280_000);
        UpfrontCosts costs = UpfrontCosts.of(price, PurchaseCostsPolicy.madridSecondHand());

        FinancingPlan plan = FinancingPlan.compute(price, Money.euros(50_000), Money.euros(5_000), costs,
                LTV_MI_PRIMERA_VIVIENDA);

        // 50.000 - 5.000 - 28.000 = 17.000, por encima del minimo del 5% (14.000).
        assertThat(plan.downPayment()).isEqualTo(Money.euros(17_000));
        assertThat(plan.loanAmount()).isEqualTo(Money.euros(263_000));
        assertThat(plan.effectiveLoanToValue().value().doubleValue()).isCloseTo(93.93,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("sin programa de aval el LTV maximo es el 80% estandar")
    void standardLtvRequiresBiggerDownPayment() {
        Money price = Money.euros(200_000);
        UpfrontCosts costs = UpfrontCosts.of(price, PurchaseCostsPolicy.madridSecondHand());

        FinancingPlan plan = FinancingPlan.compute(price, Money.euros(30_000), Money.zero(), costs,
                Percentage.of("80.00"));

        // Exige 40.000 de entrada + 20.000 de gastos = 60.000.
        assertThat(plan.savingsSufficient()).isFalse();
        assertThat(plan.downPayment()).isEqualTo(Money.euros(40_000));
    }
}
