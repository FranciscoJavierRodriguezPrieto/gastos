package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/**
 * Entrada del motor de simulacion. Cada control deslizante de la herramienta de
 * hipoteca mapea uno a uno con un campo de este record, de modo que reproducir un
 * escenario es tan simple como volver a enviarlo.
 *
 * @param targetReserve fondo de emergencia que el hogar prefiere no destinar a la
 *                      entrada; cero significa "aporto todo el ahorro"
 */
public record SimulationRequest(Money propertyPrice,
                                Money availableSavings,
                                Money targetReserve,
                                Percentage annualNominalRate,
                                int termYears,
                                ApplicantProfile applicant) {

    public SimulationRequest {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(availableSavings, "availableSavings");
        Guard.notNull(targetReserve, "targetReserve");
        Guard.notNull(annualNominalRate, "annualNominalRate");
        Guard.inRange(termYears, LoanTerms.MIN_TERM_YEARS, LoanTerms.MAX_TERM_YEARS, "termYears");
        Guard.notNull(applicant, "applicant");
        if (!propertyPrice.isPositive()) {
            throw new DomainException("El precio de la vivienda debe ser mayor que cero");
        }
        if (availableSavings.isNegative() || targetReserve.isNegative()) {
            throw new DomainException("El ahorro y el fondo de emergencia no pueden ser negativos");
        }
    }

    /** Variante del mismo escenario con otro precio: base del motor de ajuste dinamico. */
    public SimulationRequest withPropertyPrice(Money newPrice) {
        return new SimulationRequest(newPrice, availableSavings, targetReserve, annualNominalRate,
                termYears, applicant);
    }

    public SimulationRequest withAnnualNominalRate(Percentage newRate) {
        return new SimulationRequest(propertyPrice, availableSavings, targetReserve, newRate, termYears,
                applicant);
    }

    public SimulationRequest withTermYears(int newTermYears) {
        return new SimulationRequest(propertyPrice, availableSavings, targetReserve, annualNominalRate,
                newTermYears, applicant);
    }

    public SimulationRequest withNetMonthlyIncome(Money newIncome) {
        ApplicantProfile updated = new ApplicantProfile(newIncome, applicant.otherMonthlyDebts(),
                applicant.age(), applicant.firstHome());
        return new SimulationRequest(propertyPrice, availableSavings, targetReserve, annualNominalRate,
                termYears, updated);
    }
}
