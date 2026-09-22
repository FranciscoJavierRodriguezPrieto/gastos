package com.gastos.iam.infrastructure.rest;

import com.gastos.iam.application.PasskeyAuthenticationOptions;
import com.gastos.iam.application.PasskeyRegistrationOptions;
import com.gastos.iam.domain.model.PasskeyCredential;
import com.gastos.iam.infrastructure.rest.dto.PasskeyAuthenticationOptionsResponse;
import com.gastos.iam.infrastructure.rest.dto.PasskeyRegistrationOptionsResponse;
import com.gastos.iam.infrastructure.rest.dto.PasskeyResponse;
import java.util.List;

/** Traduce entre el contrato WebAuthn del navegador y el dominio. */
public final class PasskeyRestMapper {

    private static final String PUBLIC_KEY = "public-key";

    /**
     * Se exige verificacion de usuario y credencial descubrible.
     *
     * <p>Van aqui y no en configuracion porque no son ajustes de despliegue: relajarlos
     * cambiaria lo que significa entrar con passkey en esta aplicacion.</p>
     */
    private static final PasskeyRegistrationOptionsResponse.AuthenticatorSelection SELECCION =
            new PasskeyRegistrationOptionsResponse.AuthenticatorSelection("required", "required");

    private PasskeyRestMapper() {
    }

    public static PasskeyRegistrationOptionsResponse toResponse(PasskeyRegistrationOptions options) {
        return new PasskeyRegistrationOptionsResponse(
                options.challenge(),
                new PasskeyRegistrationOptionsResponse.RelyingParty(
                        options.relyingPartyId(), options.relyingPartyName()),
                new PasskeyRegistrationOptionsResponse.UserHandle(
                        options.userHandle(), options.userName(), options.userDisplayName()),
                options.algorithms().stream()
                        .map(alg -> new PasskeyRegistrationOptionsResponse.CredentialParameter(
                                PUBLIC_KEY, alg))
                        .toList(),
                options.excludeCredentialIds().stream()
                        .map(id -> new PasskeyRegistrationOptionsResponse.CredentialDescriptor(
                                PUBLIC_KEY, id))
                        .toList(),
                options.timeoutMillis(),
                // Sin attestation: saber que marca de autenticador se usa no aporta nada a
                // un hogar de dos personas, y pedirla anade una huella identificativa del
                // dispositivo que luego habria que custodiar.
                "none",
                SELECCION);
    }

    public static PasskeyAuthenticationOptionsResponse toResponse(
            PasskeyAuthenticationOptions options) {
        return new PasskeyAuthenticationOptionsResponse(options.challenge(),
                options.relyingPartyId(), options.timeoutMillis(), "required");
    }

    public static PasskeyResponse toResponse(PasskeyCredential credential) {
        return new PasskeyResponse(credential.id(), credential.label(), credential.backupEligible(),
                credential.createdAt(), credential.lastUsedAt());
    }

    public static List<PasskeyResponse> toResponses(List<PasskeyCredential> credentials) {
        return credentials.stream().map(PasskeyRestMapper::toResponse).toList();
    }
}
