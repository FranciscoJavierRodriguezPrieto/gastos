package com.gastos.accounts.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio de alias de una cuenta. */
public record RenameAccountRequest(

        @NotBlank(message = "El alias es obligatorio")
        @Size(max = 60, message = "El alias no puede superar los 60 caracteres")
        String alias) {
}
