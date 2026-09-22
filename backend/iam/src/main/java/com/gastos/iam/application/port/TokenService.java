package com.gastos.iam.application.port;

import com.gastos.shared.domain.AuthenticatedUser;
import java.time.Duration;

/**
 * Puerto de emision de tokens.
 *
 * <p>El caso de uso decide CUANDO se emite un token y con que identidad; este puerto
 * decide COMO. Que la firma sea HMAC o RSA, y que el token de refresco sea opaco o no,
 * son detalles de infraestructura que no deben filtrarse a la logica de autenticacion.</p>
 */
public interface TokenService {

    /** Token de acceso firmado, de vida corta. */
    String issueAccessToken(AuthenticatedUser user);

    Duration accessTokenTtl();

    /** Token de refresco opaco y aleatorio. Se devuelve al cliente una sola vez. */
    String newRefreshToken();

    /** Hash del token de refresco; es lo unico que se guarda. */
    String hashRefreshToken(String rawRefreshToken);

    Duration refreshTokenTtl();
}
