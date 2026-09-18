package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Canje o revocacion de un token de refresco. */
public record RefreshRequest(

        @NotBlank(message = "El token de refresco es obligatorio")
        @Size(max = 200, message = "El token no tiene un formato valido")
        String refreshToken) {
}
