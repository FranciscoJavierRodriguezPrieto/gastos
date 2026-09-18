package com.gastos.iam.application;

import com.gastos.shared.domain.AuthenticatedUser;

/**
 * Resultado de autenticarse.
 *
 * @param refreshToken token en claro; es la unica vez que existe fuera del cliente,
 *                     porque en base de datos solo se guarda su hash
 */
public record AuthenticationResult(String accessToken,
                                   String refreshToken,
                                   long expiresInSeconds,
                                   AuthenticatedUser user) {
}
