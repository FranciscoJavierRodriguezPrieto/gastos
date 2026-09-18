package com.gastos.mortgage.infrastructure.rest;

import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.DebtToIncomeRatio;
import com.gastos.mortgage.domain.model.FinancingChoice;
import com.gastos.mortgage.domain.model.FinancingDecision;
import com.gastos.mortgage.domain.model.FinancingMode;
import com.gastos.mortgage.domain.model.FinancingPlan;
import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.model.ProgramEligibility;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.model.UpfrontCosts;
import com.gastos.mortgage.domain.model.ViabilityAssessment;
import com.gastos.mortgage.infrastructure.rest.dto.ScenarioResponseDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationRequestDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.FinancingDecisionDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.FinancingPlanDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.ProgramEligibilityDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.UpfrontCostsDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.ViabilityDto;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Traduce entre el contrato HTTP de la herramienta de hipoteca y el dominio.
 *
 * <p>La traduccion es bidireccional a proposito: {@link #toDto(SimulationRequest)}
 * permite devolver un escenario guardado en el mismo formato en que se envio, de modo
 * que el cliente pueda reabrirlo y seguir moviendo los deslizadores sin conversiones
 * propias.</p>
 */
public final class MortgageRestMapper {

    private MortgageRestMapper() {
    }

    public static SimulationRequest toDomain(SimulationRequestDto dto) {
        ApplicantProfile applicant = new ApplicantProfile(
                Money.euros(dto.netMonthlyIncome()),
                Money.euros(dto.otherMonthlyDebts()),
                dto.applicantAge(),
                dto.firstHome());

        return new SimulationRequest(
                Money.euros(dto.propertyPrice()),
                Money.euros(dto.availableSavings()),
                Money.euros(dto.targetReserve()),
                Percentage.of(dto.annualNominalRate()),
                dto.termYears(),
                applicant,
                toFinancingChoice(dto));
    }

    public static SimulationRequestDto toDto(SimulationRequest request) {
        FinancingChoice financing = request.financing();
        return new SimulationRequestDto(
                request.propertyPrice().amount(),
                request.availableSavings().amount(),
                request.targetReserve().amount(),
                request.annualNominalRate().value(),
                request.termYears(),
                request.applicant().netMonthlyIncome().amount(),
                request.applicant().otherMonthlyDebts().amount(),
                request.applicant().age(),
                request.applicant().firstHome(),
                financing.mode().name(),
                financing.programId(),
                financing.manualLoanToValue() == null ? null : financing.manualLoanToValue().value());
    }

    public static SimulationResponseDto toResponse(SimulationResult result) {
        return new SimulationResponseDto(
                result.monthlyPayment().amount(),
                result.totalInterest().amount(),
                result.totalCostOfOwnership().amount(),
                result.cashRequiredAtSigning().amount(),
                toDto(result.upfrontCosts()),
                toDto(result.financingPlan()),
                toDto(result.financingDecision()),
                toDto(result.viability()));
    }

    public static ScenarioResponseDto toResponse(MortgageScenario scenario, SimulationResult result) {
        return new ScenarioResponseDto(
                scenario.id(),
                scenario.name(),
                toDto(scenario.request()),
                toResponse(result),
                scenario.createdAt(),
                scenario.updatedAt());
    }

    /** Si no se indica modo se asume AUTOMATICO: el caso de quien solo mueve deslizadores. */
    private static FinancingChoice toFinancingChoice(SimulationRequestDto dto) {
        FinancingMode mode = parseMode(dto.financingMode());
        return switch (mode) {
            case AUTOMATICO -> FinancingChoice.automatic();
            case PROGRAMA -> FinancingChoice.program(dto.programId());
            case MANUAL -> FinancingChoice.manual(Percentage.of(dto.manualLoanToValue()));
        };
    }

    private static FinancingMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return FinancingMode.AUTOMATICO;
        }
        try {
            return FinancingMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Modo de financiacion no valido. Valores admitidos: "
                    + Arrays.stream(FinancingMode.values()).map(Enum::name)
                            .collect(Collectors.joining(", ")), e);
        }
    }

    private static UpfrontCostsDto toDto(UpfrontCosts costs) {
        return new UpfrontCostsDto(
                costs.transferTax().amount(),
                costs.ancillaryCosts().amount(),
                costs.total().amount());
    }

    private static FinancingPlanDto toDto(FinancingPlan plan) {
        return new FinancingPlanDto(
                plan.loanAmount().amount(),
                plan.downPayment().amount(),
                plan.cashRequired().amount(),
                plan.savingsBuffer().amount(),
                plan.maxLoanToValue().value(),
                plan.effectiveLoanToValue().value(),
                plan.savingsSufficient(),
                plan.shortfall().amount());
    }

    private static FinancingDecisionDto toDto(FinancingDecision decision) {
        List<ProgramEligibilityDto> evaluations = decision.evaluations().stream()
                .map(MortgageRestMapper::toDto)
                .toList();

        return new FinancingDecisionDto(
                decision.mode().name(),
                decision.mode().displayName(),
                decision.appliedLoanToValue().value(),
                decision.appliedProgramId(),
                decision.appliedProgramName(),
                evaluations,
                decision.notes());
    }

    private static ProgramEligibilityDto toDto(ProgramEligibility eligibility) {
        return new ProgramEligibilityDto(
                eligibility.programId(),
                eligibility.programName(),
                eligibility.maxLoanToValue().value(),
                eligibility.active(),
                eligibility.eligible(),
                eligibility.unmetCriteria());
    }

    private static ViabilityDto toDto(ViabilityAssessment viability) {
        DebtToIncomeRatio dti = viability.debtToIncome();
        return new ViabilityDto(
                viability.verdict().name(),
                viability.verdict().displayName(),
                viability.isViable(),
                dti.housingRatio().value(),
                dti.totalRatio().value(),
                dti.residualIncome().amount(),
                viability.blockingReasons(),
                viability.warnings());
    }
}
