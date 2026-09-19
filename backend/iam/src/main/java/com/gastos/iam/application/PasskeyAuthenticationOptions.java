package com.gastos.iam.application;

/**
 * Lo que necesita el navegador para firmar un acceso.
 *
 * <p>No lleva lista de credenciales admitidas, y es intencionado: con la lista vacia el
 * navegador ofrece las passkeys que ya tiene guardadas para este dominio, asi que se entra
 * sin escribir el correo. Ademas, una lista de credenciales servida a un anonimo diria
 * que cuentas existen en la instalacion.</p>
 */
public record PasskeyAuthenticationOptions(String challenge,
                                           String relyingPartyId,
                                           long timeoutMillis) {
}
