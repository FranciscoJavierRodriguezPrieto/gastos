package com.gastos.accounts.infrastructure.rest.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Cuerpo de entrada para dar de alta una cuenta.
 *
 * <p>No se pide el IBAN: los saldos se introducen a mano y no hay integracion bancaria,
 * asi que el alias y el nombre del banco identifican la cuenta de sobra. Guardar un dato
 * personal que no se usa solo anade obligaciones de proteccion sin aportar nada.</p>
 */
public record AccountRequest(

        @NotBlank(message = "El alias es obligatorio")
        @Size(max = 60, message = "El alias no puede superar los 60 caracteres")
        String alias,

        @NotBlank(message = "El banco es obligatorio")
        @Size(max = 60, message = "El nombre del banco no puede superar los 60 caracteres")
        String bankName,

        @NotBlank(message = "El tipo de cuenta es obligatorio")
        String type,

        @NotBlank(message = "La titularidad es obligatoria")
        String ownership,

        @NotEmpty(message = "La cuenta necesita al menos un titular")
        @Size(max = 2, message = "El hogar admite como maximo dos titulares")
        Set<UUID> holders,

        @NotNull(message = "El saldo inicial es obligatorio")
        @Digits(integer = 12, fraction = 2, message = "El saldo admite como maximo dos decimales")
        BigDecimal initialBalance) {
}
