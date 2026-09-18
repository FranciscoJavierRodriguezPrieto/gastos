package com.gastos.mortgage.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Programa de ayuda tal y como lo pinta la pantalla de configuracion.
 *
 * <p>Los limites nulos significan "sin limite" y la interfaz los muestra como tal.</p>
 */
public record AidProgramResponse(UUID id,
                                 String name,
                                 BigDecimal maxLoanToValue,
                                 BigDecimal maxPropertyPrice,
                                 Integer maxApplicantAge,
                                 boolean requiresFirstHome,
                                 boolean active,
                                 String sourceNote) {
}
