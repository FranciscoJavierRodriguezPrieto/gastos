package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.PasswordResetToken;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.Optional;

/** Puerto de salida de los tokens de restablecimiento. */
public interface PasswordResetTokenRepository {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    PasswordResetToken save(PasswordResetToken token);

    /**
     * Invalida los tokens vivos de un usuario.
     *
     * <p>Se llama al emitir uno nuevo: si alguien pide el enlace dos veces, solo debe
     * servir el ultimo. Y al completar un restablecimiento, para que no queden enlaces
     * sueltos en el buzon que sigan funcionando.</p>
     */
    void invalidateAllForUser(UserId userId, Instant now);
}
