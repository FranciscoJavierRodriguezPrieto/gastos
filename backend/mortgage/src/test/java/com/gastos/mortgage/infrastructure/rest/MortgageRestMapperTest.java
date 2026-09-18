package com.gastos.mortgage.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.FinancingMode;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationRequestDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Mapper REST de hipoteca")
class MortgageRestMapperTest {

    private static final HouseholdId HOUSEHOLD = HouseholdId.newId();

    private final MortgageSimulator simulator = MortgageSimulator.madridDefaults();
    private final AidProgram miPrimeraVivienda = AidProgram.create(HOUSEHOLD, "Mi Primera Vivienda",
            Percentage.of("95.00"), Money.euros(390_000), 35, true, true, "Verificar en BOCM");

    private static SimulationRequestDto dto(long price, long savings, long reserve, String rate,
                                            int years, long income, long debts, int age) {
        return dto(price, savings, reserve, rate, years, income, debts, age, null, null, null);
    }

    private static SimulationRequestDto dto(long price, long savings, long reserve, String rate,
                                            int years, long income, long debts, int age,
                                            String mode, UUID programId, String manualLtv) {
        return new SimulationRequestDto(
                BigDecimal.valueOf(price), BigDecimal.valueOf(savings), BigDecimal.valueOf(reserve),
                new BigDecimal(rate), years, BigDecimal.valueOf(income), BigDecimal.valueOf(debts),
                age, true, mode, programId, manualLtv == null ? null : new BigDecimal(manualLtv));
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
    @DisplayName("omitir el modo de financiacion equivale a pedir AUTOMATICO")
    void missingModeDefaultsToAutomatic() {
        SimulationRequest request = MortgageRestMapper.toDomain(
                dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32));

        assertThat(request.financing().mode()).isEqualTo(FinancingMode.AUTOMATICO);
    }

    @Test
    @DisplayName("el modo MANUAL conserva el LTV escrito por el usuario en la ida y vuelta")
    void manualModeRoundTrip() {
        SimulationRequestDto original =
                dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32, "MANUAL", null, "100.00");

        SimulationRequestDto returned = MortgageRestMapper.toDto(MortgageRestMapper.toDomain(original));

        assertThat(returned.financingMode()).isEqualTo("MANUAL");
        assertThat(returned.manualLoanToValue()).isEqualByComparingTo("100.00");
        assertThat(returned.programId()).isNull();
    }

    @Test
    @DisplayName("el modo PROGRAMA conserva el identificador elegido")
    void programModeRoundTrip() {
        UUID programId = UUID.randomUUID();
        SimulationRequestDto original =
                dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32, "PROGRAMA", programId, null);

        SimulationRequestDto returned = MortgageRestMapper.toDto(MortgageRestMapper.toDomain(original));

        assertThat(returned.financingMode()).isEqualTo("PROGRAMA");
        assertThat(returned.programId()).isEqualTo(programId);
        assertThat(returned.manualLoanToValue()).isNull();
    }

    @Test
    @DisplayName("rechaza un modo de financiacion inexistente indicando los admitidos")
    void rejectsUnknownMode() {
        SimulationRequestDto request =
                dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32, "LO_QUE_SEA", null, null);

        assertThatThrownBy(() -> MortgageRestMapper.toDomain(request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("AUTOMATICO");
    }

    @Test
    @DisplayName("la respuesta traslada cuota, ratios y desglose de gastos")
    void responseCarriesEveryFigure() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32));

        SimulationResponseDto response = MortgageRestMapper.toResponse(
                simulator.simulate(request, List.of(miPrimeraVivienda)));

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
    @DisplayName("la decision de financiacion explica que programa se aplico y como quedo cada uno")
    void financingDecisionIsExplained() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32));

        SimulationResponseDto response = MortgageRestMapper.toResponse(
                simulator.simulate(request, List.of(miPrimeraVivienda)));

        assertThat(response.financingDecision().mode()).isEqualTo("AUTOMATICO");
        assertThat(response.financingDecision().modeLabel()).isEqualTo("Automatico");
        assertThat(response.financingDecision().appliedLoanToValue()).isEqualByComparingTo("95.00");
        assertThat(response.financingDecision().appliedProgramName()).isEqualTo("Mi Primera Vivienda");
        assertThat(response.financingDecision().evaluations()).singleElement()
                .satisfies(evaluation -> {
                    assertThat(evaluation.eligible()).isTrue();
                    assertThat(evaluation.unmetCriteria()).isEmpty();
                });
        assertThat(response.financingDecision().notes()).isNotEmpty();
    }

    @Test
    @DisplayName("un programa incumplido viaja con el motivo concreto del incumplimiento")
    void unmetCriteriaTravelToTheClient() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(450_000, 200_000, 0, "3.00", 30, 6_000, 0, 42));

        SimulationResponseDto response = MortgageRestMapper.toResponse(
                simulator.simulate(request, List.of(miPrimeraVivienda)));

        assertThat(response.financingDecision().appliedProgramId()).isNull();
        assertThat(response.financingDecision().appliedLoanToValue()).isEqualByComparingTo("80.00");
        assertThat(response.financingDecision().evaluations()).singleElement()
                .satisfies(evaluation -> assertThat(evaluation.unmetCriteria()).hasSize(2));
    }

    @Test
    @DisplayName("el veredicto viaja con su etiqueta y sus motivos ya redactados")
    void verdictTravelsWithReasons() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(280_000, 10_000, 0, "1.78", 20, 3_400, 225, 32));

        SimulationResponseDto response = MortgageRestMapper.toResponse(
                simulator.simulate(request, List.of(miPrimeraVivienda)));

        assertThat(response.viability().verdict()).isEqualTo("INVIABLE");
        assertThat(response.viability().verdictLabel()).isEqualTo("Inviable");
        assertThat(response.viability().viable()).isFalse();
        assertThat(response.viability().blockingReasons()).isNotEmpty();
    }

    @Test
    @DisplayName("un escenario optimo no lleva ni bloqueos ni advertencias")
    void optimalScenarioHasNoReasons() {
        SimulationRequest request =
                MortgageRestMapper.toDomain(dto(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32));

        SimulationResponseDto response = MortgageRestMapper.toResponse(
                simulator.simulate(request, List.of(miPrimeraVivienda)));

        assertThat(response.viability().verdict()).isEqualTo("OPTIMA");
        assertThat(response.viability().blockingReasons()).isEmpty();
        assertThat(response.viability().warnings()).isEmpty();
        assertThat(response.viability().residualIncome().doubleValue())
                .isCloseTo(3_117.30, Offset.offset(1.0));
    }
}
