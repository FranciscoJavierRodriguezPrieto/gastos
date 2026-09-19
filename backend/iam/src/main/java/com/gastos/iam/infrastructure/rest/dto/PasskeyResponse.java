package com.gastos.iam.infrastructure.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Passkey registrada, tal y como se lista en "Tu cuenta".
 *
 * <p>No incluye ni el identificador de la credencial ni el material criptografico: para
 * que la persona reconozca y borre un dispositivo bastan el nombre y las fechas, y todo
 * lo demas solo seria superficie de exposicion (OWASP API3).</p>
 *
 * @param syncedToCloud la passkey puede copiarse a la cuenta del fabricante, asi que
 *                      sobrevive a la perdida del dispositivo. Es el indicador BE del
 *                      estandar y no cambia nunca; conviene que la persona sepa si la
 *                      unica que le queda vive solo en ese aparato
 */
public record PasskeyResponse(UUID id,
                              String label,
                              boolean syncedToCloud,
                              Instant createdAt,
                              Instant lastUsedAt) {
}
