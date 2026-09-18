package com.gastos.accounts.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Representacion de salida de una cuenta.
 *
 * <p>{@code maskedIban} es deliberadamente la unica vista del IBAN que cruza la
 * frontera: no existe ningun campo con el valor completo, asi que no hay forma de
 * filtrarlo por descuido al anadir una pantalla nueva (OWASP API3, exposicion
 * excesiva de datos).</p>
 */
public record AccountResponse(UUID id,
                              String alias,
                              String bankName,
                              String maskedIban,
                              String type,
                              String ownership,
                              Set<UUID> holders,
                              BigDecimal balance,
                              Instant balanceUpdatedAt) {
}
