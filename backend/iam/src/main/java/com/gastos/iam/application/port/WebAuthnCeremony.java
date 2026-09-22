package com.gastos.iam.application.port;

import java.time.Duration;
import java.util.List;

/**
 * Puerto de la criptografia WebAuthn.
 *
 * <p>Verificar una respuesta WebAuthn es analizar CBOR, descodificar una clave COSE y
 * comprobar una firma. Nada de eso es una regla de negocio, y escribirlo a mano seria
 * pedir un fallo de seguridad. Detras de este puerto vive una libreria especializada.</p>
 *
 * <p>El puerto habla en {@code String} base64url y {@code byte[]} justamente para que el
 * caso de uso no conozca ni un solo tipo de esa libreria: cambiarla no deberia obligar a
 * tocar el dominio.</p>
 */
public interface WebAuthnCeremony {

    /** Reto aleatorio en base64url. */
    String newChallenge();

    /** Cuanto tiempo se acepta una ceremonia empezada. */
    Duration challengeTtl();

    /** Dominio de la parte confiante; tiene que ser el del frontend, no el de la API. */
    String relyingPartyId();

    String relyingPartyName();

    /** Algoritmos de clave publica que se admiten, en orden de preferencia. */
    List<Integer> supportedAlgorithms();

    /**
     * Comprueba el alta de una passkey.
     *
     * @throws CeremonyFailedException si la respuesta no verifica
     */
    RegisteredPasskey verifyRegistration(String challenge, String clientDataJson,
                                         String attestationObject);

    /**
     * Comprueba una firma de acceso contra la clave publica guardada.
     *
     * @param attestedCredentialData el material tal y como lo devolvio el alta
     * @return el contador de firmas que anuncia el autenticador
     * @throws CeremonyFailedException si la firma no verifica
     */
    long verifyAssertion(String challenge, byte[] attestedCredentialData, long knownSignatureCount,
                         boolean backupEligible, String clientDataJson, String authenticatorData,
                         String signature, String userHandle);

    /**
     * Resultado del alta.
     *
     * @param credentialId           identificador de la credencial, en base64url
     * @param attestedCredentialData material criptografico serializado, opaco fuera del adaptador
     * @param backupEligible               la passkey esta sincronizada en la nube del fabricante
     */
    record RegisteredPasskey(String credentialId,
                             byte[] attestedCredentialData,
                             long signatureCount,
                             boolean backupEligible) {
    }

    /**
     * La ceremonia no verifica.
     *
     * <p>Un unico tipo de error para todos los motivos —reto que no cuadra, origen
     * equivocado, firma invalida, CBOR corrupto— por la misma razon que en el login: el
     * detalle solo le sirve a quien esta probando.</p>
     */
    class CeremonyFailedException extends RuntimeException {
        public CeremonyFailedException(String motivoInterno, Throwable causa) {
            super(motivoInterno, causa);
        }
    }
}
