package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio de contrasena con la sesion ya iniciada. */
public record ChangePasswordRequest(

        @NotBlank(message = "La contrasena actual es obligatoria")
        @Size(max = 128, message = "La contrasena es demasiado larga")
        String currentPassword,

        @NotBlank(message = "La contrasena nueva es obligatoria")
        @Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
        String newPassword) {
}
