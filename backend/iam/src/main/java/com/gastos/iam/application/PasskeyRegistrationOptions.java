package com.gastos.iam.application;

import java.util.List;

/**
 * Lo que necesita el navegador para crear una passkey.
 *
 * <p>Es el equivalente de {@code PublicKeyCredentialCreationOptions} del estandar, pero
 * en tipos propios: el DTO que viaja por HTTP lo construye el mapper, de modo que un
 * cambio en el contrato del navegador no llegue hasta aqui.</p>
 *
 * @param userHandle           identificador del usuario, en base64url; el autenticador lo
 *                             guarda y lo devuelve al entrar, y es lo que permite acceder
 *                             sin escribir el correo
 * @param excludeCredentialIds passkeys que ya tiene: el navegador se niega a registrar dos
 *                             veces el mismo dispositivo en vez de crear un duplicado
 */
public record PasskeyRegistrationOptions(String challenge,
                                         String relyingPartyId,
                                         String relyingPartyName,
                                         String userHandle,
                                         String userName,
                                         String userDisplayName,
                                         List<Integer> algorithms,
                                         List<String> excludeCredentialIds,
                                         long timeoutMillis) {
}
