package com.gastos.iam.application.port;

import com.gastos.iam.domain.model.InvitationCode;
import java.time.Duration;

/**
 * Puerto de los codigos de invitacion.
 *
 * <p>Separado de {@link ResetTokenService} por el mismo motivo que aquel lo esta de
 * {@link TokenService}: comparten tecnica —valor aleatorio, se guarda el hash— pero no
 * ciclo de vida ni formato. El de restablecimiento son 256 bits que nadie lee; este son
 * 60 bits que alguien teclea, y vive dias en vez de minutos. Juntarlos invitaria a
 * reutilizar duraciones y longitudes que no tienen nada que ver.</p>
 */
public interface InvitationCodeService {

    /** Codigo nuevo, aleatorio. Se entrega una sola vez, al generarlo. */
    InvitationCode newCode();

    /** Hash del codigo; es lo unico que se guarda. */
    String hashCode(InvitationCode code);

    Duration invitationTtl();

    /** Enlace del frontend que lleva la pantalla de alta con el codigo ya puesto. */
    String joinUrl(InvitationCode code);
}
