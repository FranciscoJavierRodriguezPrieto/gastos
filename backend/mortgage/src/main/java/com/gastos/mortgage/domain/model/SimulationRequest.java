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
 * @param financing     como se decide el LTV maximo: automatico, un programa concreto o
 *                      a mano
 */
public record SimulationRequest(Money propertyPrice,
                                Money availableSavings,
                                Money targetReserve,
                                Percentage annualNominalRate,
                                int termYears,
                                ApplicantProfile applicant,
                                FinancingChoice financing) {

    /** Atajo para el caso habitual: dejar que el motor elija el mejor programa. */
    public SimulationRequest(Money propertyPrice, Money availableSavings, Money targetReserve,
                             Percentage annualNominalRate, int termYears, ApplicantProfile applicant) {
        this(propertyPrice, availableSavings, targetReserve, annualNominalRate, termYears, applicant,
                FinancingChoice.automatic());
    }

    public SimulationRequest {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(availableSavings, "availableSavings");
        Guard.notNull(targetReserve, "targetReserve");
        Guard.notNull(annualNominalRate, "annualNominalRate");
        Guard.inRange(termYears, LoanTerms.MIN_TERM_YEARS, LoanTerms.MAX_TERM_YEARS, "termYears");
        Guard.notNull(applicant, "applicant");
        Guard.notNull(financing, "financing");
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
                termYears, applicant, financing);
    }

    public SimulationRequest withAnnualNominalRate(Percentage newRate) {
        return new SimulationRequest(propertyPrice, availableSavings, targetReserve, newRate, termYears,
                applicant, financing);
    }

    public SimulationRequest withTermYears(int newTermYears) {
        return new SimulationRequest(propertyPrice, availableSavings, targetReserve, annualNominalRate,
                newTermYears, applicant, financing);
    }

    public SimulationRequest withNetMonthlyIncome(Money newIncome) {
        // Se conserva el resto del perfil: perder aqui la situacion familiar cambiaria el
        // ITP y los programas aplicables a mitad de un barrido de ingresos.
        return new SimulationRequest(propertyPrice, availableSavings, targetReserve, annualNominalRate,
                termYears, applicant.withNetMonthlyIncome(newIncome), financing);
    }

    public SimulationRequest withFinancing(FinancingChoice newFinancing) {
        return new SimulationRequest(propertyPrice, availableSavings, targetReserve, annualNominalRate,
                termYears, applicant, newFinancing);
    }
}
