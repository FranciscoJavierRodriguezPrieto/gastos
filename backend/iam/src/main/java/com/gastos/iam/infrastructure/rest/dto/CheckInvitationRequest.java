package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Comprobacion del codigo antes de pedir los datos del alta.
 *
 * <p>El codigo va en el cuerpo y no en la ruta a proposito: lo que viaja en la URL acaba
 * en los registros de acceso del servidor y de cualquier intermediario, y esto es una
 * credencial.</p>
 */
public record CheckInvitationRequest(

        @NotBlank(message = "El codigo es obligatorio")
        // Holgado respecto a los 12 caracteres reales: quien pega el codigo arrastra
        // guiones y espacios, y normalizarlo es cosa del dominio, no de esta validacion.
        @Size(max = 40, message = "El codigo no tiene un formato valido")
        String code) {
}
