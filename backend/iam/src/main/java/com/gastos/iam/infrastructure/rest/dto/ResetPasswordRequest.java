package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Restablecimiento con el token recibido por correo. */
public record ResetPasswordRequest(

        @NotBlank(message = "El token es obligatorio")
        @Size(max = 200, message = "El token no tiene un formato valido")
        String token,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
        String newPassword) {
}
