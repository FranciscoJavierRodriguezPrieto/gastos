package com.gastos.mortgage.domain.service;

import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.DebtToIncomeRatio;
import com.gastos.mortgage.domain.model.FinancingDecision;
import com.gastos.mortgage.domain.model.FinancingMode;
import com.gastos.mortgage.domain.model.FinancingPlan;
import com.gastos.mortgage.domain.model.ViabilityAssessment;
import com.gastos.mortgage.domain.model.ViabilityVerdict;
import com.gastos.mortgage.domain.policy.LendingPolicy;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduce los numeros de la operacion a un veredicto de viabilidad.
 *
 * <p>Separado del calculo de la cuota a proposito: la matematica financiera es
 * universal, mientras que los umbrales de riesgo son una decision de negocio que
 * cambiara. Un unico motivo de cambio por clase (SRP).</p>
 *
 * <p>El analisis es el mismo se financie con ayuda publica, sin ella o con un LTV
 * escrito a mano. Lo unico que cambia es el texto de la advertencia cuando se supera el
 * LTV estandar, porque no es igual depender de un aval concreto que de una cifra que el
 * usuario ha supuesto.</p>
 */
public final class ViabilityAnalyzer {

    private static final BigDecimal EMERGENCY_FUND_MONTHS = BigDecimal.valueOf(3);

    private final LendingPolicy policy;

    public ViabilityAnalyzer(LendingPolicy policy) {
        this.policy = Guard.notNull(policy, "policy");
    }

    public ViabilityAssessment analyze(FinancingPlan plan, Money monthlyPayment,
                                       ApplicantProfile applicant, FinancingDecision financing) {
        Guard.notNull(plan, "plan");
        Guard.notNull(monthlyPayment, "monthlyPayment");
        Guard.notNull(applicant, "applicant");
        Guard.notNull(financing, "financing");

        DebtToIncomeRatio dti = new DebtToIncomeRatio(applicant.netMonthlyIncome(), monthlyPayment,
                applicant.otherMonthlyDebts());

        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!plan.savingsSufficient()) {
            blocking.add("Ahorro insuficiente para entrada y gastos: faltan " + plan.shortfall());
        }
        if (dti.housingRatio().isGreaterThan(policy.maxHousingDti())) {
            blocking.add("DTI vivienda del " + dti.housingRatio() + ", supera el limite del "
                    + policy.maxHousingDti());
        }
        if (dti.totalRatio().isGreaterThan(policy.maxTotalDti())) {
            blocking.add("DTI total del " + dti.totalRatio() + ", supera el limite del "
                    + policy.maxTotalDti());
        }
        if (dti.residualIncome().isLessThan(policy.minResidualIncome())) {
            blocking.add("Renta disponible tras cuotas de " + dti.residualIncome()
                    + ", por debajo del minimo de " + policy.minResidualIncome());
        }

        if (blocking.isEmpty()) {
            if (dti.housingRatio().isGreaterThan(policy.optimalHousingDti())) {
                warnings.add("DTI vivienda del " + dti.housingRatio() + ", por encima de la zona comoda del "
                        + policy.optimalHousingDti());
            }
            if (dti.totalRatio().isGreaterThan(policy.optimalTotalDti())) {
                warnings.add("DTI total del " + dti.totalRatio() + ", por encima de la zona comoda del "
                        + policy.optimalTotalDti());
            }
            highLoanToValueWarning(plan, financing).ifPresent(warnings::add);
            Money emergencyFund = monthlyPayment.multipliedBy(EMERGENCY_FUND_MONTHS);
            if (plan.savingsBuffer().isLessThan(emergencyFund)) {
                warnings.add("Tras la firma quedan " + plan.savingsBuffer()
                        + ", menos de tres cuotas de colchon");
            }
        }

        ViabilityVerdict verdict;
        if (!blocking.isEmpty()) {
            verdict = ViabilityVerdict.INVIABLE;
        } else if (warnings.isEmpty()) {
            verdict = ViabilityVerdict.OPTIMA;
        } else {
            verdict = ViabilityVerdict.VIABLE_AJUSTADA;
        }

        return new ViabilityAssessment(verdict, dti, monthlyPayment, blocking, warnings);
    }

    private java.util.Optional<String> highLoanToValueWarning(FinancingPlan plan,
                                                              FinancingDecision financing) {
        if (!plan.effectiveLoanToValue().isGreaterThan(policy.standardLoanToValue())) {
            return java.util.Optional.empty();
        }
        // Se informa del LTV efectivo, no del techo: el techo es lo maximo que dejarian
        // pedir, y lo que de verdad se pide depende del ahorro que se aporte.
        String ltv = plan.effectiveLoanToValue().toString();
        if (financing.usesAidProgram()) {
            return java.util.Optional.of("LTV efectivo del " + ltv
                    + ": la operacion depende del aval de '" + financing.appliedProgramName() + "'");
        }
        if (financing.mode() == FinancingMode.MANUAL) {
            return java.util.Optional.of("LTV efectivo del " + ltv + ", por encima del "
                    + policy.standardLoanToValue() + " estandar. El techo del "
                    + financing.appliedLoanToValue() + " lo has fijado a mano: confirma con la "
                    + "entidad que financia ese porcentaje");
        }
        return java.util.Optional.of("LTV efectivo del " + ltv + ", por encima del "
                + policy.standardLoanToValue() + " que concede la banca sin ayuda publica");
    }
}
