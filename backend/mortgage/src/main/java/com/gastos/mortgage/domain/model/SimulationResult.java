package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/** Salida completa del motor de simulacion, lista para pintar en la herramienta. */
public record SimulationResult(SimulationRequest request,
                               UpfrontCosts upfrontCosts,
                               FinancingPlan financingPlan,
                               Money monthlyPayment,
                               Money totalInterest,
                               FinancingDecision financingDecision,
                               ViabilityAssessment viability) {

    public SimulationResult {
        Guard.notNull(request, "request");
        Guard.notNull(upfrontCosts, "upfrontCosts");
        Guard.notNull(financingPlan, "financingPlan");
        Guard.notNull(monthlyPayment, "monthlyPayment");
        Guard.notNull(totalInterest, "totalInterest");
        Guard.notNull(financingDecision, "financingDecision");
        Guard.notNull(viability, "viability");
    }

    public Percentage housingDti() {
        return viability.debtToIncome().housingRatio();
    }

    public Percentage totalDti() {
        return viability.debtToIncome().totalRatio();
    }

    /** Desembolso total el dia de la firma: entrada mas impuestos y gastos. */
    public Money cashRequiredAtSigning() {
        return financingPlan.cashRequired();
    }

    /** Coste total de la operacion: precio, intereses y gastos iniciales. */
    public Money totalCostOfOwnership() {
        return request.propertyPrice().plus(totalInterest).plus(upfrontCosts.total());
    }
}
