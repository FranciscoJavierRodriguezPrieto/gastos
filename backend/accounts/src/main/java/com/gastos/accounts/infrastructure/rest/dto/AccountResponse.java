package com.gastos.accounts.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Representacion de salida de una cuenta.
 *
 * <p>Lista fija y explicita de campos: lo que no este aqui no puede llegar al cliente
 * por descuido al anadir una pantalla nueva (OWASP API3, exposicion excesiva de datos).
 * En particular no se expone {@code householdId}.</p>
 */
public record AccountResponse(UUID id,
                              String alias,
                              String bankName,
                              String type,
                              String ownership,
                              Set<UUID> holders,
                              BigDecimal balance,
                              Instant balanceUpdatedAt) {
}
