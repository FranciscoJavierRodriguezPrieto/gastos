package com.gastos.mortgage.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationRequestDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto;
import java.math.BigDecimal;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Mapper REST de hipoteca")
class MortgageRestMapperTest {

    private final MortgageSimulator simulator = MortgageSimulator.madridDefaults();

    private static SimulationRequestDto dto(long price, long savings, long reserve, String rate,
                                            int years, long income, long debts, int age) {
        return new SimulationRequestDto(
                BigDecimal.valueOf(price), BigDecimal.valueOf(savings), BigDecimal.valueOf(reserve),
                new BigDecimal(rate), years, BigDecimal.valueOf(income), BigDecimal.valueOf(debts),
                age, true);
    }

    @Test
    @DisplayName("la ida y vuelta entre DTO y dominio conserva todos los valores")
    void roundTripPreservesValues() {
        SimulationRequestDto original = dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32);

        SimulationRequestDto returned = MortgageRestMapper.toDto(MortgageRestMapper.toDomain(original));

        assertThat(returned.propertyPrice()).isEqualByComparingTo(original.propertyPrice());
        assertThat(returned.availableSavings()).isEqualByComparingTo(original.availableSavings());
        assertThat(returned.targetReserve()).isEqualByComparingTo(original.targetReserve());
        assertThat(returned.annualNominalRate()).isEqualByComparingTo(original.annualNominalRate());
        assertThat(returned.termYears()).isEqualTo(original.termYears());
        assertThat(returned.netMonthlyIncome()).isEqualByComparingTo(original.netMonthlyIncome());
        assertThat(returned.otherMonthlyDebts()).isEqualByComparingTo(original.otherMonthlyDebts());
        assertThat(returned.applicantAge()).isEqualTo(original.applicantAge());
        assertThat(returned.firstHome()).isEqualTo(original.firstHome());
    }

    @Test
    @DisplayName("la respuesta traslada cuota, ratios y desglose de gastos")
    void responseCarriesEveryFigure() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32));
        SimulationResult result = simulator.simulate(request);

        SimulationResponseDto response = MortgageRestMapper.toResponse(result);

        assertThat(response.monthlyPayment().doubleValue()).isCloseTo(1_108.82, Offset.offset(1.0));
        assertThat(response.upfrontCosts().transferTax()).isEqualByComparingTo("16800.00");
        assertThat(response.upfrontCosts().ancillaryCosts()).isEqualByComparingTo("11200.00");
        assertThat(response.upfrontCosts().total()).isEqualByComparingTo("28000.00");
        assertThat(response.financing().loanAmount()).isEqualByComparingTo("263000.00");
        assertThat(response.financing().savingsSufficient()).isTrue();
        assertThat(response.financing().effectiveLoanToValue().doubleValue())
                .isCloseTo(93.93, Offset.offset(0.01));
    }

    @Test
    @DisplayName("el veredicto viaja con su etiqueta y sus motivos ya redactados")
    void verdictTravelsWithReasons() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(280_000, 10_000, 0, "1.78", 20, 3_400, 225, 32));

        SimulationResponseDto response = MortgageRestMapper.toResponse(simulator.simulate(request));

        assertThat(response.viability().verdict()).isEqualTo("INVIABLE");
        assertThat(response.viability().verdictLabel()).isEqualTo("Inviable");
        assertThat(response.viability().viable()).isFalse();
        assertThat(response.viability().blockingReasons()).isNotEmpty();
        assertThat(response.viability().housingDti()).isNotNull();
        assertThat(response.viability().totalDti()).isNotNull();
    }

    @Test
    @DisplayName("un escenario optimo no lleva ni bloqueos ni advertencias")
    void optimalScenarioHasNoReasons() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32));

        SimulationResponseDto response = MortgageRestMapper.toResponse(simulator.simulate(request));

        assertThat(response.viability().verdict()).isEqualTo("OPTIMA");
        assertThat(response.viability().blockingReasons()).isEmpty();
        assertThat(response.viability().warnings()).isEmpty();
        assertThat(response.viability().residualIncome().doubleValue())
                .isCloseTo(3_117.30, Offset.offset(1.0));
    }
}
