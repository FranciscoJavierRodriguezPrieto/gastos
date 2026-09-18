package com.gastos.mortgage.infrastructure.rest;

import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.DebtToIncomeRatio;
import com.gastos.mortgage.domain.model.FinancingPlan;
import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.model.UpfrontCosts;
import com.gastos.mortgage.domain.model.ViabilityAssessment;
import com.gastos.mortgage.infrastructure.rest.dto.ScenarioResponseDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationRequestDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.FinancingPlanDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.UpfrontCostsDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto.ViabilityDto;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

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
                applicant);
    }

    public static SimulationRequestDto toDto(SimulationRequest request) {
        return new SimulationRequestDto(
                request.propertyPrice().amount(),
                request.availableSavings().amount(),
                request.targetReserve().amount(),
                request.annualNominalRate().value(),
                request.termYears(),
                request.applicant().netMonthlyIncome().amount(),
                request.applicant().otherMonthlyDebts().amount(),
                request.applicant().age(),
                request.applicant().firstHome());
    }

    public static SimulationResponseDto toResponse(SimulationResult result) {
        return new SimulationResponseDto(
                result.monthlyPayment().amount(),
                result.totalInterest().amount(),
                result.totalCostOfOwnership().amount(),
                result.cashRequiredAtSigning().amount(),
                toDto(result.upfrontCosts()),
                toDto(result.financingPlan()),
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
