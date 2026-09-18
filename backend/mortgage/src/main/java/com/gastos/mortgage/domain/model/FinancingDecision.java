package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Percentage;
import java.util.List;
import java.util.UUID;

/**
 * Como se ha llegado al LTV aplicado, y por que.
 *
 * <p>La decision viaja completa hasta la interfaz: que modo se uso, que programa se
 * aplico (si alguno), que LTV salio y como quedo cada programa evaluado. Asi la pantalla
 * puede explicar el resultado sin volver a razonarlo por su cuenta.</p>
 *
 * @param appliedProgramId   {@code null} si no se aplico ningun programa
 * @param appliedProgramName {@code null} si no se aplico ningun programa
 * @param notes              explicaciones en texto de por que salio este LTV
 */
public record FinancingDecision(FinancingMode mode,
                                Percentage appliedLoanToValue,
                                UUID appliedProgramId,
                                String appliedProgramName,
                                List<ProgramEligibility> evaluations,
                                List<String> notes) {

    public FinancingDecision {
        Guard.notNull(mode, "mode");
        Guard.notNull(appliedLoanToValue, "appliedLoanToValue");
        evaluations = List.copyOf(Guard.notNull(evaluations, "evaluations"));
        notes = List.copyOf(Guard.notNull(notes, "notes"));
    }

    public boolean usesAidProgram() {
        return appliedProgramId != null;
    }

    /** Programas que el hogar cumple en este escenario. */
    public List<ProgramEligibility> eligiblePrograms() {
        return evaluations.stream().filter(ProgramEligibility::eligible).toList();
    }
}
