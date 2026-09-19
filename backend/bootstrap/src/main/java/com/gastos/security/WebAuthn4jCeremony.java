package com.gastos.security;

import com.gastos.iam.application.port.WebAuthnCeremony;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.server.ServerProperty;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Verificacion WebAuthn con WebAuthn4J.
 *
 * <p>Es el unico sitio del proyecto que sabe que existe esa libreria. Hacia arriba solo
 * salen cadenas en base64url y bytes opacos, tal y como declara el puerto.</p>
 *
 * <p>Se usa el gestor <em>no estricto</em>, que no valida cadenas de certificados de
 * attestation. Es coherente con pedir {@code attestation: "none"}: en un hogar de dos
 * personas no aporta nada comprobar la marca del autenticador, y exigirlo obligaria a
 * mantener al dia un almacen de raices de fabricantes. Lo que si se comprueba —y es lo
 * que importa— es el origen, el reto, la presencia y verificacion del usuario y la firma
 * criptografica.</p>
 */
@Service
public class WebAuthn4jCeremony implements WebAuthnCeremony {

    private static final int CHALLENGE_BYTES = 32;

    /**
     * ES256 (-7) primero y RS256 (-257) como alternativa.
     *
     * <p>ES256 es lo que usan de serie Apple, Google y las llaves FIDO2. RS256 se deja
     * detras para autenticadores de Windows Hello antiguos que no ofrecen curva eliptica;
     * el navegador elige el primero que su autenticador sepa hacer.</p>
     */
    private static final List<Integer> ALGORITMOS = List.of(-7, -257);

    private final WebAuthnManager manager;
    private final AttestedCredentialDataConverter credentialDataConverter;
    private final WebAuthnProperties properties;
    private final Set<Origin> origins;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebAuthn4jCeremony(WebAuthnProperties properties) {
        this.properties = properties;
        ObjectConverter objectConverter = new ObjectConverter();
        this.manager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);
        this.credentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
        this.origins = new LinkedHashSet<>(properties.origins().stream().map(Origin::new).toList());
    }

    @Override
    public String newChallenge() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        secureRandom.nextBytes(bytes);
        return encode(bytes);
    }

    @Override
    public Duration challengeTtl() {
        return properties.ttl();
    }

    @Override
    public String relyingPartyId() {
        return properties.rpId();
    }

    @Override
    public String relyingPartyName() {
        return properties.rpName();
    }

    @Override
    public List<Integer> supportedAlgorithms() {
        return ALGORITMOS;
    }

    @Override
    public RegisteredPasskey verifyRegistration(String challenge, String clientDataJson,
                                                String attestationObject) {
        RegistrationData datos;
        try {
            datos = manager.verify(
                    new RegistrationRequest(decode(attestationObject), decode(clientDataJson)),
                    // Se exige presencia y verificacion de usuario: la passkey solo vale
                    // si el autenticador ha pedido huella, cara o PIN.
                    new RegistrationParameters(serverProperty(challenge), true, true));
        } catch (RuntimeException e) {
            throw new CeremonyFailedException("El alta de la passkey no verifica", e);
        }

        AuthenticatorData<?> authData = datos.getAttestationObject().getAuthenticatorData();
        AttestedCredentialData credencial = authData.getAttestedCredentialData();
        if (credencial == null) {
            throw new CeremonyFailedException("La respuesta no trae credencial", null);
        }

        return new RegisteredPasskey(
                encode(credencial.getCredentialId()),
                credentialDataConverter.convert(credencial),
                authData.getSignCount(),
                // BE, no BS: si la credencial PUEDE copiarse a la nube del fabricante. Es
                // inmutable, al contrario que BS, que cambia segun este sincronizada o no.
                authData.isFlagBE());
    }

    @Override
    public long verifyAssertion(String challenge, byte[] attestedCredentialData,
                                long knownSignatureCount, boolean backupEligible,
                                String clientDataJson, String authenticatorData, String signature,
                                String userHandle) {
        AttestedCredentialData credencial;
        try {
            credencial = credentialDataConverter.convert(attestedCredentialData);
        } catch (RuntimeException e) {
            // El material guardado no se deja leer: es un fallo nuestro, no del cliente.
            throw new IllegalStateException("Material de passkey corrupto en base de datos", e);
        }

        AuthenticationData datos;
        try {
            datos = manager.verify(
                    new AuthenticationRequest(
                            credencial.getCredentialId(),
                            userHandle == null || userHandle.isBlank() ? null : decode(userHandle),
                            decode(authenticatorData),
                            decode(clientDataJson),
                            decode(signature)),
                    new AuthenticationParameters(serverProperty(challenge),
                            credentialRecord(credencial, knownSignatureCount, backupEligible),
                            null, true));
        } catch (RuntimeException e) {
            throw new CeremonyFailedException("La firma de acceso no verifica", e);
        }

        return datos.getAuthenticatorData().getSignCount();
    }

    /**
     * Reconstruye lo minimo que la libreria necesita para verificar una firma.
     *
     * <p>Faltan a proposito la attestation y el {@code clientData} del alta: no se
     * guardan, porque no hacen falta para comprobar una firma y solo serian mas datos que
     * custodiar. Lo que si viaja es {@code backupEligible}, porque la libreria comprueba
     * que ese indicador no cambie entre el alta y el acceso.</p>
     */
    private static CredentialRecord credentialRecord(AttestedCredentialData credencial,
                                                     long signatureCount, boolean backupEligible) {
        return new CredentialRecordImpl(
                null,
                true,
                backupEligible,
                null,
                signatureCount,
                credencial,
                new AuthenticationExtensionsAuthenticatorOutputs<>(),
                null,
                null,
                null);
    }

    private ServerProperty serverProperty(String challenge) {
        return new ServerProperty(origins, properties.rpId(), new DefaultChallenge(decode(challenge)));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lo que llega del cliente puede ser cualquier cosa; un base64 roto no es un 500. */
    private static byte[] decode(String base64url) {
        try {
            return Base64.getUrlDecoder().decode(base64url);
        } catch (IllegalArgumentException e) {
            throw new CeremonyFailedException("Campo que no es base64url", e);
        }
    }
}
