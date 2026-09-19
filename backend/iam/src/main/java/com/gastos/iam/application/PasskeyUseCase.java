package com.gastos.iam.application;

import com.gastos.iam.application.port.WebAuthnCeremony;
import com.gastos.iam.domain.model.PasskeyCeremony;
import com.gastos.iam.domain.model.PasskeyChallenge;
import com.gastos.iam.domain.model.PasskeyCredential;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.port.PasskeyChallengeRepository;
import com.gastos.iam.domain.port.PasskeyCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.ResourceNotFoundException;
import com.gastos.shared.domain.UserId;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alta y uso de passkeys.
 *
 * <p>Una passkey sustituye a la contrasena, no la complementa: al entrar con ella se
 * emiten exactamente los mismos tokens que con el login normal. La contrasena sigue
 * existiendo como via de recuperacion, porque quedarse fuera de una aplicacion con el
 * historico financiero del hogar por haber perdido el movil seria peor que el riesgo que
 * se evita.</p>
 *
 * <p>Cuatro reglas que sostienen la seguridad de todo esto:</p>
 *
 * <ol>
 *   <li><strong>El reto se consume antes de verificar</strong>, no despues. Si se
 *       consumiera al final, un fallo de verificacion dejaria el reto vivo y se podria
 *       reintentar con el mismo indefinidamente.</li>
 *   <li><strong>Un reto sirve solo para su ceremonia y su usuario.</strong> El alta exige
 *       sesion iniciada; sin esta comprobacion, un reto de alta podria presentarse en el
 *       endpoint de acceso, que es publico.</li>
 *   <li><strong>El identificador de usuario que devuelve el autenticador tiene que
 *       cuadrar</strong> con el dueno de la credencial. Es barato y cierra la posibilidad
 *       de que una credencial se asocie a otra cuenta.</li>
 *   <li><strong>Borrar una passkey exige ser su dueno</strong>, no basta con estar
 *       autenticado. Es el caso de libro de OWASP API1.</li>
 * </ol>
 */
public class PasskeyUseCase {

    private static final Logger log = LoggerFactory.getLogger(PasskeyUseCase.class);

    private final UserRepository users;
    private final PasskeyCredentialRepository credentials;
    private final PasskeyChallengeRepository challenges;
    private final WebAuthnCeremony ceremony;
    private final AuthenticateUseCase authenticate;
    private final Clock clock;

    public PasskeyUseCase(UserRepository users, PasskeyCredentialRepository credentials,
                          PasskeyChallengeRepository challenges, WebAuthnCeremony ceremony,
                          AuthenticateUseCase authenticate, Clock clock) {
        this.users = Guard.notNull(users, "users");
        this.credentials = Guard.notNull(credentials, "credentials");
        this.challenges = Guard.notNull(challenges, "challenges");
        this.ceremony = Guard.notNull(ceremony, "ceremony");
        this.authenticate = Guard.notNull(authenticate, "authenticate");
        this.clock = Guard.notNull(clock, "clock");
    }

    /** Prepara el alta de una passkey para quien ya ha iniciado sesion. */
    public PasskeyRegistrationOptions startRegistration(AuthenticatedUser solicitante) {
        Guard.notNull(solicitante, "solicitante");
        User usuario = users.findById(solicitante.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        PasskeyChallenge reto = emitirReto(PasskeyCeremony.REGISTRO, usuario.id());

        return new PasskeyRegistrationOptions(
                reto.challenge(),
                ceremony.relyingPartyId(),
                ceremony.relyingPartyName(),
                userHandleOf(usuario.id()),
                usuario.email().value(),
                usuario.displayName(),
                ceremony.supportedAlgorithms(),
                credentials.findAllByUser(usuario.id()).stream()
                        .map(PasskeyCredential::credentialId)
                        .toList(),
                ceremony.challengeTtl().toMillis());
    }

    /** Guarda la passkey si la respuesta del autenticador verifica. */
    public PasskeyCredential finishRegistration(AuthenticatedUser solicitante, String challenge,
                                                String clientDataJson, String attestationObject,
                                                String label) {
        Guard.notNull(solicitante, "solicitante");
        Guard.notBlank(label, "label");

        consumirReto(challenge, PasskeyCeremony.REGISTRO, solicitante.userId());

        WebAuthnCeremony.RegisteredPasskey verificada;
        try {
            verificada = ceremony.verifyRegistration(challenge, clientDataJson, attestationObject);
        } catch (WebAuthnCeremony.CeremonyFailedException e) {
            log.info("Alta de passkey rechazada para el usuario {}: {}",
                    solicitante.userId(), e.getMessage());
            throw new InvalidPasskeyException();
        }

        // El navegador ya evita el duplicado con excludeCredentials, pero eso es una
        // comprobacion del cliente y el cliente no manda aqui.
        if (credentials.findByCredentialId(verificada.credentialId()).isPresent()) {
            throw new PasskeyAlreadyRegisteredException();
        }

        PasskeyCredential credencial = credentials.save(PasskeyCredential.register(
                solicitante.userId(),
                verificada.credentialId(),
                verificada.attestedCredentialData(),
                verificada.signatureCount(),
                verificada.backupEligible(),
                label.trim(),
                clock.instant()));

        log.info("Passkey dada de alta para el usuario {}", solicitante.userId());
        return credencial;
    }

    /** Prepara un acceso. Es publico: todavia no se sabe quien esta llamando. */
    public PasskeyAuthenticationOptions startAuthentication() {
        PasskeyChallenge reto = emitirReto(PasskeyCeremony.ACCESO, null);
        return new PasskeyAuthenticationOptions(reto.challenge(), ceremony.relyingPartyId(),
                ceremony.challengeTtl().toMillis());
    }

    /** Verifica la firma y emite los tokens de sesion. */
    public AuthenticationResult finishAuthentication(String challenge, String credentialId,
                                                     String clientDataJson,
                                                     String authenticatorData, String signature,
                                                     String userHandle) {
        consumirReto(challenge, PasskeyCeremony.ACCESO, null);

        PasskeyCredential credencial = credentials.findByCredentialId(credentialId)
                .orElseThrow(() -> {
                    log.info("Acceso con una passkey desconocida");
                    return new InvalidPasskeyException();
                });

        if (userHandle != null && !userHandle.isBlank()
                && !userHandle.equals(userHandleOf(credencial.userId()))) {
            log.warn("La passkey {} llega con un identificador de usuario que no le corresponde",
                    credencial.id());
            throw new InvalidPasskeyException();
        }

        long contador;
        try {
            contador = ceremony.verifyAssertion(challenge, credencial.attestedCredentialData(),
                    credencial.signatureCount(), credencial.backupEligible(), clientDataJson,
                    authenticatorData, signature, userHandle);
        } catch (WebAuthnCeremony.CeremonyFailedException e) {
            log.info("Acceso con passkey rechazado: {}", e.getMessage());
            throw new InvalidPasskeyException();
        }

        try {
            credencial.recordUse(contador, clock.instant());
        } catch (PasskeyCredential.ClonedCredentialException e) {
            // La firma es valida pero el contador no avanza. Se deniega el acceso y se
            // deja rastro: es el unico sintoma que da un autenticador copiado.
            log.warn("Contador de firmas no creciente en la passkey {}: posible clonado",
                    credencial.id());
            throw new InvalidPasskeyException();
        }
        credentials.save(credencial);

        User usuario = users.findById(credencial.userId())
                .orElseThrow(InvalidPasskeyException::new);

        log.info("Acceso con passkey del usuario {}", usuario.id());
        return authenticate.issueTokensFor(usuario);
    }

    public List<PasskeyCredential> list(AuthenticatedUser solicitante) {
        Guard.notNull(solicitante, "solicitante");
        return credentials.findAllByUser(solicitante.userId());
    }

    /**
     * Borra una passkey propia.
     *
     * <p>Se responde igual si no existe y si es de otro: decir que no es suya confirmaria
     * que ese identificador existe.</p>
     */
    public void delete(AuthenticatedUser solicitante, UUID passkeyId) {
        Guard.notNull(solicitante, "solicitante");
        PasskeyCredential credencial = credentials.findById(passkeyId)
                .filter(c -> c.belongsTo(solicitante.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("Passkey no encontrada"));

        credentials.delete(credencial.id());
        log.info("Passkey {} eliminada por su propietario", passkeyId);
    }

    private PasskeyChallenge emitirReto(PasskeyCeremony ceremonia, UserId userId) {
        Instant now = clock.instant();
        // Se aprovecha cada emision para barrer lo caducado: sin proceso programado, y
        // el coste es una sentencia sobre una tabla que nunca crece mucho.
        challenges.deleteExpired(now);

        return challenges.save(PasskeyChallenge.issue(ceremony.newChallenge(), ceremonia, userId,
                now, now.plus(ceremony.challengeTtl())));
    }

    private void consumirReto(String challenge, PasskeyCeremony esperada, UserId esperado) {
        Guard.notBlank(challenge, "challenge");
        Instant now = clock.instant();

        PasskeyChallenge reto = challenges.findByChallenge(challenge)
                .orElseThrow(InvalidPasskeyException::new);

        if (!reto.isUsable(now) || !reto.matches(esperada, esperado)) {
            throw new InvalidPasskeyException();
        }

        reto.consume(now);
        challenges.save(reto);
    }

    /**
     * Identificador de usuario para el autenticador: los 16 bytes del UUID en base64url.
     *
     * <p>No se usa el correo. El autenticador guarda este valor y lo ensena al elegir
     * cuenta, asi que un correo ahi acabaria visible en dispositivos compartidos; ademas,
     * el estandar pide explicitamente que no sea informacion personal.</p>
     */
    private static String userHandleOf(UserId userId) {
        UUID uuid = userId.value();
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    /** Se traduce a 401, con el mismo mensaje sea cual sea el motivo. */
    public static class InvalidPasskeyException extends RuntimeException {
        public InvalidPasskeyException() {
            super("No se ha podido verificar la passkey");
        }
    }

    /** Se traduce a 409: el navegador ya deberia haberlo evitado. */
    public static class PasskeyAlreadyRegisteredException extends RuntimeException {
        public PasskeyAlreadyRegisteredException() {
            super("Esta passkey ya estaba registrada");
        }
    }
}
