package com.gastos.mortgage.infrastructure.rest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta o modificacion de un programa de ayuda.
 *
 * <p>Los limites opcionales se envian como {@code null} para decir "sin limite": un
 * programa sin tope de precio deja {@code maxPropertyPrice} vacio.</p>
 *
 * @param sourceNote de donde salen estas cifras; sirve para saber que hay que reverificar
 */
public record AidProgramRequest(

        @NotBlank(message = "El nombre del programa es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar los 80 caracteres")
        String name,

        @NotNull(message = "El LTV maximo es obligatorio")
        @DecimalMin(value = "0.01", message = "El LTV maximo debe ser mayor que cero")
        @DecimalMax(value = "100.00", message = "El LTV maximo no puede superar el 100%")
        @Digits(integer = 3, fraction = 2, message = "El LTV admite como maximo dos decimales")
        BigDecimal maxLoanToValue,

        @DecimalMin(value = "1.00", message = "El precio maximo debe ser mayor que cero")
        @Digits(integer = 9, fraction = 2, message = "El precio admite como maximo dos decimales")
        BigDecimal maxPropertyPrice,

        @Min(value = 18, message = "La edad maxima minima admitida es 18")
        @Max(value = 120, message = "La edad maxima no puede superar 120")
        Integer maxApplicantAge,

        @NotNull(message = "Indique si el programa exige primera vivienda")
        Boolean requiresFirstHome,

        /**
         * Solo para familias numerosas, monoparentales o con hijos menores a cargo.
         * Opcional: si falta se entiende que no, que es lo que hacian los clientes antes
         * de que existiera el campo.
         */
        Boolean requiresFamily,

        @NotNull(message = "Indique si el programa esta activo")
        Boolean active,

        @Size(max = 300, message = "La nota de origen no puede superar los 300 caracteres")
        String sourceNote) {

    public boolean requiresFamilyOrDefault() {
        return Boolean.TRUE.equals(requiresFamily);
    }
}
