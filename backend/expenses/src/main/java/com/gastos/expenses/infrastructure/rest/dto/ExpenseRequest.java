package com.gastos.expenses.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de entrada para crear o modificar un gasto.
 *
 * <p>Primera linea de defensa: el DTO valida forma y rango antes de que nada toque el
 * dominio, y sus campos son exactamente los que el cliente puede enviar. Un DTO
 * explicito cierra el <em>mass assignment</em>: aunque el JSON traiga
 * {@code householdId} o {@code id}, no hay donde escribirlos.</p>
 *
 * @param amount importe en euros, siempre positivo; el signo lo aporta el concepto
 */
public record ExpenseRequest(

        @NotBlank(message = "La descripcion es obligatoria")
        @Size(max = 140, message = "La descripcion no puede superar los 140 caracteres")
        String description,

        @NotNull(message = "El importe es obligatorio")
        @DecimalMin(value = "0.01", message = "El importe debe ser mayor que cero")
        @Digits(integer = 9, fraction = 2, message = "El importe admite como maximo dos decimales")
        BigDecimal amount,

        @NotBlank(message = "La categoria es obligatoria")
        String category,

        @NotBlank(message = "La periodicidad es obligatoria")
        String recurrence,

        @NotNull(message = "La fecha del gasto es obligatoria")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate incurredOn,

        UUID accountId) {
}
