package com.gastos.iam.application;

import java.time.Instant;

/**
 * Estado de la invitacion del hogar, para que la pantalla del titular sepa que ofrecer.
 *
 * <p>No incluye el codigo, ni siquiera parcialmente: no se guarda en claro y no hay
 * forma de recuperarlo. Si se ha perdido, se genera otro.</p>
 *
 * @param householdFull el hogar ya tiene sus dos convivientes, asi que no hay a quien
 *                      invitar
 * @param expiresAt     null si no hay invitacion vigente
 */
public record InvitationStatus(boolean pending, Instant expiresAt, boolean householdFull) {

    public static InvitationStatus none(boolean householdFull) {
        return new InvitationStatus(false, null, householdFull);
    }
}
