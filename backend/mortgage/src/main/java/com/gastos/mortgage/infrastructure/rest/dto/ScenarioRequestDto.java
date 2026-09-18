package com.gastos.mortgage.infrastructure.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta o modificacion de un escenario guardado. */
public record ScenarioRequestDto(

        @NotBlank(message = "El nombre del escenario es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar los 60 caracteres")
        String name,

        @NotNull(message = "Los datos de la simulacion son obligatorios")
        @Valid
        SimulationRequestDto simulation) {
}
