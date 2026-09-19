package com.gastos.iam.application;

import com.gastos.iam.application.port.ResetTokenService;
import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.PasswordPolicy;
import com.gastos.iam.domain.model.PasswordResetToken;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.model.UserCredential;
import com.gastos.iam.domain.port.EmailSender;
import com.gastos.iam.domain.port.PasswordHasher;
import com.gastos.iam.domain.port.PasswordResetTokenRepository;
import com.gastos.iam.domain.port.RefreshTokenRepository;
import com.gastos.iam.domain.port.UserCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recuperar y cambiar la contrasena.
 *
 * <p>Tres decisiones de seguridad:</p>
 *
 * <ol>
 *   <li><strong>Pedir el enlace siempre responde igual</strong>, exista o no el correo.
 *       Si distinguiera, el formulario de "he olvidado mi contrasena" seria un
 *       comprobador de cuentas registradas, que es justo lo que el login evita.</li>
 *   <li><strong>Al restablecer se revocan todas las sesiones.</strong> Si alguien pide
 *       recuperar es porque ha perdido el control de su acceso; dejar vivas las sesiones
 *       anteriores dejaria dentro a quien no debe.</li>
 *   <li><strong>Un token nuevo invalida los anteriores.</strong> Pedir el enlace dos
 *       veces no deja dos puertas abiertas.</li>
 * </ol>
 */
public class RecoverAccessUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecoverAccessUseCase.class);

    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final ResetTokenService resetTokenService;
    private final EmailSender emailSender;
    private final Clock clock;

    public RecoverAccessUseCase(UserRepository users, UserCredentialRepository credentials,
                                PasswordResetTokenRepository resetTokens,
                                RefreshTokenRepository refreshTokens, PasswordHasher passwordHasher,
                                ResetTokenService resetTokenService, EmailSender emailSender,
                                Clock clock) {
        this.users = Guard.notNull(users, "users");
        this.credentials = Guard.notNull(credentials, "credentials");
        this.resetTokens = Guard.notNull(resetTokens, "resetTokens");
        this.refreshTokens = Guard.notNull(refreshTokens, "refreshTokens");
        this.passwordHasher = Guard.notNull(passwordHasher, "passwordHasher");
        this.resetTokenService = Guard.notNull(resetTokenService, "resetTokenService");
        this.emailSender = Guard.notNull(emailSender, "emailSender");
        this.clock = Guard.notNull(clock, "clock");
    }

    /**
     * Envia el enlace de restablecimiento si el correo corresponde a un usuario.
     *
     * <p>No devuelve nada ni falla si el correo no existe: desde fuera, pedirlo para una
     * cuenta real y para una inventada es indistinguible.</p>
     */
    public void requestReset(Email email) {
        Guard.notNull(email, "email");
        Optional<User> usuario = users.findByEmail(email);

        if (usuario.isEmpty()) {
            log.info("Solicitud de restablecimiento para un correo no registrado");
            return;
        }

        Instant now = clock.instant();
        // Un enlace nuevo invalida los anteriores: no deben quedar dos puertas abiertas.
        resetTokens.invalidateAllForUser(usuario.get().id(), now);

        String enClaro = resetTokenService.newResetToken();
        resetTokens.save(PasswordResetToken.issue(
                usuario.get().id(),
                resetTokenService.hashResetToken(enClaro),
                now,
                now.plus(resetTokenService.resetTokenTtl())));

        emailSender.send(email, "Restablecer tu contrasena de Gastos",
                cuerpoDelCorreo(usuario.get(), enClaro));
        log.info("Enlace de restablecimiento enviado al usuario {}", usuario.get().id());
    }

    /**
     * Fija la contrasena nueva a partir del token del correo.
     *
     * @return el usuario, ya listo para iniciar sesion
     */
    public User resetPassword(String rawToken, char[] newPassword) {
        Guard.notBlank(rawToken, "token");
        PasswordPolicy.validate(newPassword);

        Instant now = clock.instant();
        PasswordResetToken token = resetTokens
                .findByTokenHash(resetTokenService.hashResetToken(rawToken))
                .orElseThrow(InvalidResetTokenException::new);

        if (!token.isUsable(now)) {
            throw new InvalidResetTokenException();
        }

        User usuario = users.findById(token.userId()).orElseThrow(InvalidResetTokenException::new);

        guardarContrasena(usuario, newPassword, now);

        token.consume(now);
        resetTokens.save(token);
        resetTokens.invalidateAllForUser(usuario.id(), now);

        // Quien recupera el acceso ha perdido el control de su cuenta: fuera todas las
        // sesiones, incluidas las de quien se la hubiera llevado.
        refreshTokens.revokeAllForUser(usuario.id(), now);

        log.info("Contrasena restablecida para el usuario {}", usuario.id());
        return usuario;
    }

    /**
     * Cambia la contrasena de quien ya ha iniciado sesion.
     *
     * <p>Se exige la actual aunque la sesion sea valida: protege de que alguien que
     * encuentre el equipo desbloqueado se apodere de la cuenta.</p>
     */
    public void changePassword(AuthenticatedUser solicitante, char[] currentPassword,
                               char[] newPassword) {
        Guard.notNull(solicitante, "solicitante");
        PasswordPolicy.validate(newPassword);

        UserCredential credencial = credentials.findByUserId(solicitante.userId())
                .orElseThrow(() -> new DomainException("No hay credencial para este usuario"));

        if (!passwordHasher.matches(currentPassword, credencial.passwordHash())) {
            log.info("Cambio de contrasena rechazado para el usuario {}", solicitante.userId());
            throw new AuthenticateUseCase.InvalidCredentialsException();
        }

        Instant now = clock.instant();
        credencial.changeTo(passwordHasher.hash(newPassword), now);
        credentials.save(credencial);

        // Se caen las demas sesiones: cambiar la contrasena debe expulsar a quien
        // estuviera dentro con la anterior.
        refreshTokens.revokeAllForUser(solicitante.userId(), now);
        log.info("Contrasena cambiada por el propio usuario {}", solicitante.userId());
    }

    private void guardarContrasena(User usuario, char[] nueva, Instant now) {
        UserCredential credencial = credentials.findByUserId(usuario.id())
                .map(existente -> {
                    existente.changeTo(passwordHasher.hash(nueva), now);
                    return existente;
                })
                .orElseGet(() -> UserCredential.of(usuario.id(), passwordHasher.hash(nueva), now));
        credentials.save(credencial);
    }

    private String cuerpoDelCorreo(User usuario, String token) {
        long minutos = resetTokenService.resetTokenTtl().toMinutes();
        return """
                Hola %s:

                Has pedido restablecer la contrasena de Gastos. Abre este enlace:

                %s

                El enlace caduca en %d minutos y solo se puede usar una vez.

                Si no has sido tu, puedes ignorar este mensaje: tu contrasena actual sigue
                siendo valida y nadie ha entrado en tu cuenta.
                """.formatted(usuario.displayName(), resetTokenService.resetUrl(token), minutos);
    }

    /** Se traduce a 400 en el adaptador REST, siempre con el mismo mensaje. */
    public static class InvalidResetTokenException extends RuntimeException {
        public InvalidResetTokenException() {
            super("El enlace no es valido o ha caducado");
        }
    }
}
