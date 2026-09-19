package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Respuesta del autenticador al entrar con una passkey. */
public record AuthenticateWithPasskeyRequest(

        @NotBlank(message = "El reto es obligatorio")
        @Size(max = 128, message = "El reto no tiene un formato valido")
        String challenge,

        @NotBlank(message = "Falta la credencial")
        @Size(max = 512, message = "La credencial no tiene un formato valido")
        String credentialId,

        @NotBlank(message = "Falta la respuesta del autenticador")
        @Size(max = 4096, message = "La respuesta del autenticador es demasiado grande")
        String clientDataJSON,

        @NotBlank(message = "Falta la respuesta del autenticador")
        @Size(max = 4096, message = "La respuesta del autenticador es demasiado grande")
        String authenticatorData,

        @NotBlank(message = "Falta la firma")
        @Size(max = 2048, message = "La firma no tiene un formato valido")
        String signature,

        /** Opcional: no todos los autenticadores lo devuelven. */
        @Size(max = 128, message = "El identificador de usuario no tiene un formato valido")
        String userHandle) {
}
