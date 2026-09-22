package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de acceso.
 *
 * <p>El correo no lleva {@code @Email} a proposito: un formato invalido debe fallar como
 * credencial incorrecta, no como error de validacion. Si respondiera distinto, el
 * formulario delataria que correos tienen forma de estar dados de alta.</p>
 */
public record LoginRequest(

        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 254, message = "El correo es demasiado largo")
        String email,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(max = 128, message = "La contrasena es demasiado larga")
        String password) {
}
