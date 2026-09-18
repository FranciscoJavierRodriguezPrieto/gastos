package com.gastos.mortgage.infrastructure.rest;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.infrastructure.rest.dto.AidProgramResponse;
import com.gastos.shared.domain.Money;
import java.math.BigDecimal;
import java.util.List;

/** Traduce entre el contrato HTTP del catalogo de programas y el dominio. */
public final class AidProgramRestMapper {

    private AidProgramRestMapper() {
    }

    /** {@code null} significa "sin limite", y asi se conserva en ambos sentidos. */
    public static Money toOptionalMoney(BigDecimal amount) {
        return amount == null ? null : Money.euros(amount);
    }

    public static AidProgramResponse toResponse(AidProgram program) {
        return new AidProgramResponse(
                program.id(),
                program.name(),
                program.maxLoanToValue().value(),
                program.maxPropertyPrice() == null ? null : program.maxPropertyPrice().amount(),
                program.maxApplicantAge(),
                program.requiresFirstHome(),
                program.isActive(),
                program.sourceNote());
    }

    public static List<AidProgramResponse> toResponses(List<AidProgram> programs) {
        return programs.stream().map(AidProgramRestMapper::toResponse).toList();
    }
}
