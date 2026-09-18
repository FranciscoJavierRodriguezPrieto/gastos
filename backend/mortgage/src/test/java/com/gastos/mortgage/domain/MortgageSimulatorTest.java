package com.gastos.mortgage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.model.ViabilityVerdict;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Motor de simulacion hipotecaria")
class MortgageSimulatorTest {

    private static final Offset<Double> ONE_EURO = Offset.offset(1.0);

    private final MortgageSimulator simulator = MortgageSimulator.madridDefaults();

    private static ApplicantProfile applicant(long netIncome, long otherDebts, int age) {
        return new ApplicantProfile(Money.euros(netIncome), Money.euros(otherDebts), age, true);
    }

    @Test
    @DisplayName("INVIABLE: el ahorro no cubre el 10% de gastos no financiables")
    void inviableBecauseSavingsDoNotCoverUpfrontCosts() {
        SimulationRequest request = new SimulationRequest(
                Money.euros(280_000), Money.euros(10_000), Money.zero(),
                Percentage.of("1.78"), 20, applicant(3_400, 225, 32));

        SimulationResult result = simulator.simulate(request);

        assertThat(result.upfrontCosts().total()).isEqualTo(Money.euros(28_000));
        assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.INVIABLE);
        assertThat(result.viability().blockingReasons())
                .anyMatch(reason -> reason.startsWith("Ahorro insuficiente"));
        assertThat(result.viability().isViable()).isFalse();
    }

    @Test
    @DisplayName("INVIABLE: la cuota supera el 30% de DTI vivienda")
    void inviableBecauseHousingDtiExceedsLimit() {
        SimulationRequest request = new SimulationRequest(
                Money.euros(280_000), Money.euros(50_000), Money.euros(5_000),
                Percentage.of("3.00"), 30, applicant(2_500, 225, 32));

        SimulationResult result = simulator.simulate(request);

        assertThat(result.housingDti().value().doubleValue()).isGreaterThan(30.0);
        assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.INVIABLE);
        assertThat(result.viability().blockingReasons())
                .anyMatch(reason -> reason.contains("DTI vivienda"));
    }

    @Test
    @DisplayName("VIABLE PERO AJUSTADA: cumple limites pero depende del aval al 94% de LTV")
    void viableButTightBecauseOfHighLoanToValue() {
        SimulationRequest request = new SimulationRequest(
                Money.euros(280_000), Money.euros(50_000), Money.euros(5_000),
                Percentage.of("3.00"), 30, applicant(4_500, 225, 32));

        SimulationResult result = simulator.simulate(request);

        assertThat(result.financingPlan().loanAmount()).isEqualTo(Money.euros(263_000));
        assertThat(result.monthlyPayment().amount().doubleValue()).isCloseTo(1_108.82, Offset.offset(1.0));
        assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.VIABLE_AJUSTADA);
        assertThat(result.viability().blockingReasons()).isEmpty();
        assertThat(result.viability().warnings()).anyMatch(warning -> warning.contains("LTV"));
    }

    @Test
    @DisplayName("OPTIMA: cuota holgada, LTV del 78% y colchon tras la firma")
    void optimalScenario() {
        SimulationRequest request = new SimulationRequest(
                Money.euros(200_000), Money.euros(70_000), Money.euros(6_000),
                Percentage.of("3.00"), 30, applicant(4_000, 225, 32));

        SimulationResult result = simulator.simulate(request);

        assertThat(result.financingPlan().loanAmount()).isEqualTo(Money.euros(156_000));
        assertThat(result.monthlyPayment().amount().doubleValue()).isCloseTo(657.70, Offset.offset(1.0));
        assertThat(result.housingDti().value().doubleValue()).isCloseTo(16.44, Offset.offset(0.05));
        assertThat(result.totalDti().value().doubleValue()).isCloseTo(22.07, Offset.offset(0.05));
        assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.OPTIMA);
        assertThat(result.viability().warnings()).isEmpty();
    }

    @Test
    @DisplayName("la deuda previa del coche solo mueve el DTI total, no el de vivienda")
    void carLoanOnlyAffectsTotalDti() {
        SimulationRequest withoutCar = new SimulationRequest(
                Money.euros(200_000), Money.euros(70_000), Money.euros(6_000),
                Percentage.of("3.00"), 30, applicant(4_000, 0, 32));
        SimulationRequest withCar = new SimulationRequest(
                Money.euros(200_000), Money.euros(70_000), Money.euros(6_000),
                Percentage.of("3.00"), 30, applicant(4_000, 225, 32));

        SimulationResult a = simulator.simulate(withoutCar);
        SimulationResult b = simulator.simulate(withCar);

        assertThat(a.housingDti()).isEqualTo(b.housingDti());
        assertThat(b.totalDti().value().doubleValue())
                .isGreaterThan(a.totalDti().value().doubleValue());
        assertThat(b.viability().debtToIncome().residualIncome())
                .isEqualTo(a.viability().debtToIncome().residualIncome().minus(Money.euros(225)));
    }

    @Test
    @DisplayName("superar la edad del programa rebaja el LTV maximo al 80%")
    void losingProgramEligibilityDropsLoanToValue() {
        SimulationRequest eligible = new SimulationRequest(
                Money.euros(280_000), Money.euros(50_000), Money.euros(5_000),
                Percentage.of("3.00"), 30, applicant(4_500, 225, 32));
        SimulationRequest notEligible = new SimulationRequest(
                Money.euros(280_000), Money.euros(50_000), Money.euros(5_000),
                Percentage.of("3.00"), 30, applicant(4_500, 225, 42));

        SimulationResult withProgram = simulator.simulate(eligible);
        SimulationResult withoutProgram = simulator.simulate(notEligible);

        assertThat(withProgram.financingPlan().maxLoanToValue()).isEqualTo(Percentage.of("95.00"));
        assertThat(withoutProgram.financingPlan().maxLoanToValue()).isEqualTo(Percentage.of("80.00"));
        assertThat(withoutProgram.financingPlan().savingsSufficient()).isFalse();
        assertThat(withoutProgram.viability().verdict()).isEqualTo(ViabilityVerdict.INVIABLE);
    }

    @Test
    @DisplayName("el barrido de ingresos reduce el DTI al subir la nomina del hogar")
    void incomeSweepImprovesRatios() {
        SimulationRequest base = new SimulationRequest(
                Money.euros(280_000), Money.euros(50_000), Money.euros(5_000),
                Percentage.of("3.00"), 30, applicant(3_400, 225, 32));

        List<SimulationResult> sweep = simulator.sweepByIncome(base,
                List.of(Money.euros(3_400), Money.euros(3_700), Money.euros(4_000)));

        assertThat(sweep).hasSize(3);
        assertThat(sweep.get(0).housingDti().value().doubleValue())
                .isGreaterThan(sweep.get(2).housingDti().value().doubleValue());
        assertThat(sweep).allSatisfy(result ->
                assertThat(result.monthlyPayment()).isEqualTo(sweep.get(0).monthlyPayment()));
    }

    @Test
    @DisplayName("el barrido de tipos encarece la cuota al subir el TIN")
    void rateSweepIncreasesPayment() {
        SimulationRequest base = new SimulationRequest(
                Money.euros(200_000), Money.euros(70_000), Money.euros(6_000),
                Percentage.of("2.00"), 30, applicant(4_000, 225, 32));

        List<SimulationResult> sweep = simulator.sweepByRate(base,
                List.of(Percentage.of("2.00"), Percentage.of("3.00"), Percentage.of("4.00")));

        assertThat(sweep.get(0).monthlyPayment()).isLessThan(sweep.get(1).monthlyPayment());
        assertThat(sweep.get(1).monthlyPayment()).isLessThan(sweep.get(2).monthlyPayment());
    }

    @Test
    @DisplayName("el coste total de la operacion suma precio, intereses y gastos")
    void totalCostAggregatesEveryComponent() {
        SimulationRequest request = new SimulationRequest(
                Money.euros(200_000), Money.euros(70_000), Money.euros(6_000),
                Percentage.of("3.00"), 30, applicant(4_000, 225, 32));

        SimulationResult result = simulator.simulate(request);

        Money expected = Money.euros(200_000)
                .plus(result.totalInterest())
                .plus(result.upfrontCosts().total());
        assertThat(result.totalCostOfOwnership()).isEqualTo(expected);
        assertThat(result.cashRequiredAtSigning()).isEqualTo(Money.euros(64_000));
        assertThat(result.monthlyPayment().amount().doubleValue()).isCloseTo(657.70, ONE_EURO);
    }
}
