package com.gastos.iam.infrastructure.rest.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Alta del segundo conviviente. Solo la puede hacer el titular. */
public record AddMemberRequest(

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        @Size(max = 254, message = "El correo es demasiado largo")
        String email,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 60, message = "El nombre no puede superar los 60 caracteres")
        String displayName,

        @NotBlank(message = "La contrasena es obligatoria")
        @Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
        String password,

        @NotNull(message = "Los ingresos netos son obligatorios")
        @PositiveOrZero(message = "Los ingresos netos no pueden ser negativos")
        @Digits(integer = 7, fraction = 2, message = "Los ingresos admiten dos decimales")
        BigDecimal monthlyNetIncome) {
}
