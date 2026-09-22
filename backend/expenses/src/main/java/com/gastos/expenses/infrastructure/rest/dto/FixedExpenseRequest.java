package com.gastos.expenses.infrastructure.rest.dto;

import com.gastos.expenses.domain.model.FixedExpense;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Alta o modificacion de un gasto fijo.
 *
 * <p>No lleva periodicidad: un gasto fijo es mensual por definicion. Los de otra
 * frecuencia —un seguro anual— se siguen registrando como gastos normales con su
 * {@code Recurrence}, que es lo que el analisis de la hipoteca ya sabe prorratear.</p>
 *
 * <p>{@code startMonth} solo se tiene en cuenta al dar de alta. Cambiarlo despues
 * reescribiria meses ya cerrados, que es justo lo que este diseno evita.</p>
 */
public record FixedExpenseRequest(

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 140, message = "La descripcion no puede superar los 140 caracteres")
        String description,

        @NotNull(message = "El importe es obligatorio")
        @Positive(message = "El importe debe ser mayor que cero")
        @Digits(integer = 13, fraction = 2, message = "El importe admite dos decimales")
        BigDecimal amount,

        @NotBlank(message = "La categoria es obligatoria")
        String category,

        // El limite de 28 no es capricho: un cargo el 31 no existe en febrero.
        @Min(value = 1, message = "El dia de cargo debe estar entre 1 y 28")
        @Max(value = FixedExpense.MAX_DAY_OF_MONTH,
                message = "El dia de cargo debe estar entre 1 y 28")
        int dayOfMonth,

        UUID accountId,

        /** Formato {@code yyyy-MM}. Si no viene, empieza en el mes en curso. */
        @Size(max = 7, message = "El mes de inicio tiene formato yyyy-MM")
        String startMonth) {
}
