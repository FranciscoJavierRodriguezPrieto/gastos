package com.gastos.iam.infrastructure.rest.dto;

/**
 * Parametros para {@code navigator.credentials.get()}.
 *
 * <p>Sin {@code allowCredentials}: el navegador ensena las passkeys que ya tiene para
 * este dominio y se entra sin escribir el correo. Ademas, servir esa lista a un anonimo
 * seria decirle que cuentas existen.</p>
 */
public record PasskeyAuthenticationOptionsResponse(String challenge,
                                                   String rpId,
                                                   long timeout,
                                                   String userVerification) {
}
