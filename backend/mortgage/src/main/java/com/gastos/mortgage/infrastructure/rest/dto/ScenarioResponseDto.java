package com.gastos.mortgage.infrastructure.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Escenario guardado junto con su resultado recien recalculado.
 *
 * <p>El resultado se adjunta pero no se almacena: se recalcula en cada lectura con las
 * politicas vigentes.</p>
 */
public record ScenarioResponseDto(UUID id,
                                  String name,
                                  SimulationRequestDto simulation,
                                  SimulationResponseDto result,
                                  Instant createdAt,
                                  Instant updatedAt) {
}
