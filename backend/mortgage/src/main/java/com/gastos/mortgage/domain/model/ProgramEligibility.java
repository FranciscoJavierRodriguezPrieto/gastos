package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Percentage;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de contrastar una operacion con un programa de ayuda.
 *
 * <p>Se devuelve para <em>todos</em> los programas, no solo para el aplicado: la
 * pantalla puede asi mostrar "cumples estos dos, y del tercero te falta la edad" sin
 * volver a preguntar al servidor.</p>
 *
 * @param unmetCriteria requisitos incumplidos, vacio si es elegible
 */
public record ProgramEligibility(UUID programId,
                                 String programName,
                                 Percentage maxLoanToValue,
                                 boolean active,
                                 boolean eligible,
                                 List<String> unmetCriteria) {

    public ProgramEligibility {
        Guard.notNull(programId, "programId");
        Guard.notBlank(programName, "programName");
        Guard.notNull(maxLoanToValue, "maxLoanToValue");
        unmetCriteria = List.copyOf(Guard.notNull(unmetCriteria, "unmetCriteria"));
    }
}
