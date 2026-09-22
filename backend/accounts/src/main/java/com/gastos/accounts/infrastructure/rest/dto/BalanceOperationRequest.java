package com.gastos.accounts.infrastructure.rest.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Importe de una operacion sobre el saldo: ingreso, cargo o conciliacion.
 *
 * <p>Un DTO propio por operacion, en lugar de reutilizar {@link AccountRequest} con
 * campos opcionales, evita que un cliente pueda colar un cambio de titularidad o de
 * alias dentro de lo que parece un simple apunte.</p>
 */
public record BalanceOperationRequest(

        @NotNull(message = "El importe es obligatorio")
        @Digits(integer = 12, fraction = 2, message = "El importe admite como maximo dos decimales")
        BigDecimal amount) {
}
