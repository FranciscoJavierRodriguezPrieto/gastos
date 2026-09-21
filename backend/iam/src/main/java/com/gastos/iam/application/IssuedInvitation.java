package com.gastos.iam.application;

import com.gastos.iam.domain.model.InvitationCode;
import java.time.Instant;

/**
 * Invitacion recien emitida.
 *
 * @param code codigo en claro; es la unica vez que existe fuera de quien lo recibe,
 *             porque en base de datos solo se guarda su hash
 */
public record IssuedInvitation(InvitationCode code, Instant expiresAt, String joinUrl) {
}
