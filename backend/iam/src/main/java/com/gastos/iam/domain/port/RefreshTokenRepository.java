package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.RefreshToken;
import com.gastos.shared.domain.UserId;
import java.util.Optional;

/** Puerto de salida de tokens de refresco. */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken token);

    /** Revoca todos los tokens vivos de un usuario. Se usa al detectar reutilizacion. */
    void revokeAllForUser(UserId userId, java.time.Instant now);
}
