package com.gastos.iam.infrastructure.rest.dto;

/**
 * Pareja de tokens.
 *
 * @param refreshToken se entrega una sola vez: en base de datos solo queda su hash
 * @param expiresIn    segundos de vida del token de acceso
 */
public record TokenResponse(String accessToken,
                            String refreshToken,
                            String tokenType,
                            long expiresIn,
                            UserResponse user) {
}
