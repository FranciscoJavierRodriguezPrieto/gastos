package com.gastos.mortgage.domain.service;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.FinancingChoice;
import com.gastos.mortgage.domain.model.FinancingDecision;
import com.gastos.mortgage.domain.model.FinancingMode;
import com.gastos.mortgage.domain.model.ProgramEligibility;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decide el LTV maximo aplicable a la operacion.
 *
 * <p>Esta es la unica pieza del motor que conoce los programas de ayuda. Todo lo que
 * viene despues —cuota, DTI, veredicto— trabaja con un LTV y le da igual de donde
 * salio. Aislar aqui la variabilidad normativa es lo que permite anadir una convocatoria
 * nueva sin tocar ni una linea de matematica financiera.</p>
 */
public final class FinancingSelector {

    private final Percentage standardLoanToValue;

    public FinancingSelector(Percentage standardLoanToValue) {
        this.standardLoanToValue = Guard.notNull(standardLoanToValue, "standardLoanToValue");
    }

    public FinancingDecision decide(FinancingChoice choice, Money propertyPrice,
                                    ApplicantProfile applicant, List<AidProgram> programs) {
        Guard.notNull(choice, "choice");
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(applicant, "applicant");
        Guard.notNull(programs, "programs");

        List<ProgramEligibility> evaluations = programs.stream()
                .map(program -> program.evaluate(propertyPrice, applicant))
                .toList();

        return switch (choice.mode()) {
            case MANUAL -> manual(choice, evaluations);
            case PROGRAMA -> byProgram(choice, evaluations);
            case AUTOMATICO -> automatic(evaluations);
        };
    }

    /** El usuario manda: ni se comprueban requisitos ni se aplica ningun programa. */
    private FinancingDecision manual(FinancingChoice choice, List<ProgramEligibility> evaluations) {
        return new FinancingDecision(
                FinancingMode.MANUAL,
                choice.manualLoanToValue(),
                null,
                null,
                evaluations,
                List.of("LTV fijado a mano en el " + choice.manualLoanToValue()
                        + ". No se ha comprobado ningun requisito: confirma con la entidad que "
                        + "financia ese porcentaje."));
    }

    private FinancingDecision byProgram(FinancingChoice choice, List<ProgramEligibility> evaluations) {
        Optional<ProgramEligibility> chosen = evaluations.stream()
                .filter(evaluation -> evaluation.programId().equals(choice.programId()))
                .findFirst();

        if (chosen.isEmpty()) {
            return standard(FinancingMode.PROGRAMA, evaluations,
                    List.of("El programa seleccionado ya no existe. Se aplica la financiacion "
                            + "estandar del " + standardLoanToValue + "."));
        }

        ProgramEligibility evaluation = chosen.get();
        if (!evaluation.active()) {
            return standard(FinancingMode.PROGRAMA, evaluations,
                    List.of("El programa '" + evaluation.programName() + "' esta desactivado. "
                            + "Se aplica la financiacion estandar del " + standardLoanToValue + "."));
        }
        if (!evaluation.eligible()) {
            // No se fuerza el LTV del programa: seria simular una hipoteca que nadie va a
            // conceder. Se explica que falta y se sigue con la financiacion estandar.
            List<String> notes = new ArrayList<>();
            notes.add("No se cumplen los requisitos de '" + evaluation.programName()
                    + "'. Se aplica la financiacion estandar del " + standardLoanToValue + ".");
            notes.addAll(evaluation.unmetCriteria());
            return standard(FinancingMode.PROGRAMA, evaluations, notes);
        }

        return applyProgram(FinancingMode.PROGRAMA, evaluation, evaluations);
    }

    /** Entre los programas que se cumplen, gana el que mas financia. */
    private FinancingDecision automatic(List<ProgramEligibility> evaluations) {
        Optional<ProgramEligibility> best = evaluations.stream()
                .filter(ProgramEligibility::eligible)
                .max(Comparator.comparing(ProgramEligibility::maxLoanToValue));

        if (best.isEmpty()) {
            return standard(FinancingMode.AUTOMATICO, evaluations,
                    List.of(evaluations.isEmpty()
                            ? "No hay programas de ayuda dados de alta. Se aplica la financiacion "
                                    + "estandar del " + standardLoanToValue + "."
                            : "No se cumple ningun programa de ayuda. Se aplica la financiacion "
                                    + "estandar del " + standardLoanToValue + "."));
        }
        return applyProgram(FinancingMode.AUTOMATICO, best.get(), evaluations);
    }

    private FinancingDecision applyProgram(FinancingMode mode, ProgramEligibility evaluation,
                                           List<ProgramEligibility> evaluations) {
        // Un programa nunca puede empeorar la financiacion estandar.
        boolean improvesStandard = evaluation.maxLoanToValue().isGreaterThan(standardLoanToValue);
        if (!improvesStandard) {
            return standard(mode, evaluations,
                    List.of("'" + evaluation.programName() + "' no mejora la financiacion estandar "
                            + "del " + standardLoanToValue + ", que es la que se aplica."));
        }
        return new FinancingDecision(
                mode,
                evaluation.maxLoanToValue(),
                evaluation.programId(),
                evaluation.programName(),
                evaluations,
                List.of("Se aplica '" + evaluation.programName() + "': financiacion de hasta el "
                        + evaluation.maxLoanToValue() + " del valor de la vivienda."));
    }

    private FinancingDecision standard(FinancingMode mode, List<ProgramEligibility> evaluations,
                                       List<String> notes) {
        return new FinancingDecision(mode, standardLoanToValue, null, null, evaluations, notes);
    }
}
