package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud del enlace de restablecimiento.
 *
 * <p>Sin {@code @Email}: un formato invalido debe responder igual que un correo que no
 * existe. Validarlo aqui daria un 400 distinguible del 204 de un correo real, y eso
 * convertiria el formulario en un comprobador de cuentas.</p>
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 254, message = "El correo es demasiado largo")
        String email) {
}
