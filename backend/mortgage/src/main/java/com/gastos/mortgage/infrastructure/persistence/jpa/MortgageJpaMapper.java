package com.gastos.mortgage.infrastructure.persistence.jpa;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.FinancingChoice;
import com.gastos.mortgage.domain.model.FinancingMode;
import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.math.BigDecimal;

/** Traduce entre las filas del contexto de hipoteca y sus agregados. */
public final class MortgageJpaMapper {

    private MortgageJpaMapper() {
    }

    // ------------------------------------------------------------ programas de ayuda

    public static AidProgramEntity toEntity(AidProgram program) {
        return new AidProgramEntity(
                program.id(),
                program.householdId().value(),
                program.name(),
                program.maxLoanToValue().value(),
                program.maxPropertyPrice() == null ? null : program.maxPropertyPrice().amount(),
                program.maxApplicantAge(),
                program.requiresFirstHome(),
                program.requiresFamily(),
                program.isActive(),
                program.sourceNote());
    }

    public static AidProgram toDomain(AidProgramEntity entity) {
        return AidProgram.rehydrate(
                entity.getId(),
                new HouseholdId(entity.getHouseholdId()),
                entity.getName(),
                Percentage.of(entity.getMaxLoanToValue()),
                toMoney(entity.getMaxPropertyPrice()),
                entity.getMaxApplicantAge(),
                entity.isRequiresFirstHome(),
                entity.isRequiresFamily(),
                entity.isActive(),
                entity.getSourceNote());
    }

    // ------------------------------------------------------------ escenarios

    public static MortgageScenarioEntity toEntity(MortgageScenario scenario) {
        SimulationRequest request = scenario.request();
        FinancingChoice financing = request.financing();

        return new MortgageScenarioEntity(
                scenario.id(),
                scenario.householdId().value(),
                scenario.name(),
                request.propertyPrice().amount(),
                request.availableSavings().amount(),
                request.targetReserve().amount(),
                request.annualNominalRate().value(),
                request.termYears(),
                request.applicant().netMonthlyIncome().amount(),
                request.applicant().otherMonthlyDebts().amount(),
                request.applicant().age(),
                request.applicant().firstHome(),
                request.applicant().familyWithChildren(),
                request.applicant().largeFamily(),
                request.applicant().primaryResidence(),
                financing.mode().name(),
                financing.programId(),
                financing.manualLoanToValue() == null ? null : financing.manualLoanToValue().value(),
                scenario.createdAt(),
                scenario.updatedAt());
    }

    public static MortgageScenario toDomain(MortgageScenarioEntity entity) {
        ApplicantProfile applicant = new ApplicantProfile(
                Money.euros(entity.getNetMonthlyIncome()),
                Money.euros(entity.getOtherMonthlyDebts()),
                entity.getApplicantAge(),
                entity.isFirstHome(),
                entity.isFamilyWithChildren(),
                entity.isLargeFamily(),
                entity.isPrimaryResidence());

        SimulationRequest request = new SimulationRequest(
                Money.euros(entity.getPropertyPrice()),
                Money.euros(entity.getAvailableSavings()),
                Money.euros(entity.getTargetReserve()),
                Percentage.of(entity.getAnnualNominalRate()),
                entity.getTermYears(),
                applicant,
                toFinancingChoice(entity));

        return MortgageScenario.rehydrate(
                entity.getId(),
                new HouseholdId(entity.getHouseholdId()),
                entity.getName(),
                request,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static FinancingChoice toFinancingChoice(MortgageScenarioEntity entity) {
        return switch (FinancingMode.valueOf(entity.getFinancingMode())) {
            case AUTOMATICO -> FinancingChoice.automatic();
            case PROGRAMA -> FinancingChoice.program(entity.getFinancingProgramId());
            case MANUAL -> FinancingChoice.manual(Percentage.of(entity.getManualLoanToValue()));
        };
    }

    /** Nulo significa "sin limite", y como tal se conserva. */
    private static Money toMoney(BigDecimal amount) {
        return amount == null ? null : Money.euros(amount);
    }
}
