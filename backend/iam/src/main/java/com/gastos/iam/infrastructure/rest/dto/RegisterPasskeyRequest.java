package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Respuesta del autenticador al dar de alta una passkey.
 *
 * <p>El reto viaja de vuelta explicitamente para poder localizarlo sin analizar el
 * {@code clientDataJSON}, que es entrada sin verificar. Falsearlo no sirve de nada: la
 * libreria comprueba que el reto que va firmado dentro coincida con este.</p>
 */
public record RegisterPasskeyRequest(

        @NotBlank(message = "El reto es obligatorio")
        @Size(max = 128, message = "El reto no tiene un formato valido")
        String challenge,

        @NotBlank(message = "Falta la respuesta del autenticador")
        @Size(max = 4096, message = "La respuesta del autenticador es demasiado grande")
        String clientDataJSON,

        @NotBlank(message = "Falta la respuesta del autenticador")
        @Size(max = 8192, message = "La respuesta del autenticador es demasiado grande")
        String attestationObject,

        @NotBlank(message = "Pon un nombre para reconocer el dispositivo")
        @Size(max = 60, message = "El nombre no puede pasar de 60 caracteres")
        String label) {
}
