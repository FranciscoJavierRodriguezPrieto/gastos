package com.gastos.iam.infrastructure.rest.dto;

import java.util.List;

/**
 * Parametros para {@code navigator.credentials.create()}.
 *
 * <p>La forma sigue a {@code PublicKeyCredentialCreationOptions} del estandar para que el
 * cliente solo tenga que descodificar los campos binarios y pasarlo tal cual. Copiar aqui
 * la estructura del navegador es deliberado: el precio de unos records anidados a cambio
 * de que no haya logica de traduccion en el frontend, que es donde peor se prueba.</p>
 */
public record PasskeyRegistrationOptionsResponse(String challenge,
                                                 RelyingParty rp,
                                                 UserHandle user,
                                                 List<CredentialParameter> pubKeyCredParams,
                                                 List<CredentialDescriptor> excludeCredentials,
                                                 long timeout,
                                                 String attestation,
                                                 AuthenticatorSelection authenticatorSelection) {

    public record RelyingParty(String id, String name) {
    }

    /** {@code id} es el identificador opaco del usuario, en base64url. */
    public record UserHandle(String id, String name, String displayName) {
    }

    public record CredentialParameter(String type, int alg) {
    }

    public record CredentialDescriptor(String type, String id) {
    }

    /**
     * @param residentKey      {@code required}: la credencial se guarda en el dispositivo
     *                         con el identificador de usuario dentro, que es lo que
     *                         permite entrar sin escribir el correo
     * @param userVerification {@code required}: el autenticador tiene que pedir huella,
     *                         cara o PIN. Sin esto, un movil desbloqueado abandonado sobre
     *                         una mesa abriria las cuentas del hogar
     */
    public record AuthenticatorSelection(String residentKey, String userVerification) {
    }
}
