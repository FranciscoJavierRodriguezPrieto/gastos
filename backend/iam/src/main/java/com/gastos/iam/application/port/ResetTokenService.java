package com.gastos.iam.application.port;

import java.time.Duration;

/**
 * Puerto de los tokens de restablecimiento.
 *
 * <p>Separado de {@link TokenService} a proposito: aquel emite credenciales de sesion y
 * este una credencial de un solo uso que viaja por correo. Comparten técnica pero no
 * ciclo de vida, y mezclarlos invitaría a reutilizar duraciones que no tienen nada que
 * ver.</p>
 */
public interface ResetTokenService {

    /** Token opaco y aleatorio. Se entrega una sola vez, dentro del enlace del correo. */
    String newResetToken();

    /** Hash del token; es lo unico que se guarda. */
    String hashResetToken(String rawToken);

    Duration resetTokenTtl();

    /** Enlace completo que se incluye en el correo, ya con el token dentro. */
    String resetUrl(String rawToken);
}
