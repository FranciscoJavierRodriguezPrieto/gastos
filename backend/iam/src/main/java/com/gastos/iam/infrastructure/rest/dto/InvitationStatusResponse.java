package com.gastos.iam.infrastructure.rest.dto;

import java.time.Instant;

/**
 * Si hay una invitacion vigente y hasta cuando. Nunca incluye el codigo.
 *
 * @param householdFull el hogar ya tiene sus dos convivientes; no hay a quien invitar
 */
public record InvitationStatusResponse(boolean pending, Instant expiresAt, boolean householdFull) {
}
