package com.gastos.iam.infrastructure.persistence.jpa;

import com.gastos.iam.domain.model.PasswordResetToken;
import com.gastos.iam.domain.port.PasswordResetTokenRepository;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida de los tokens de restablecimiento. */
@Repository
@Transactional
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository tokens;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(entidad -> PasswordResetToken.rehydrate(
                entidad.getId(),
                new UserId(entidad.getUserId()),
                entidad.getTokenHash(),
                entidad.getIssuedAt(),
                entidad.getExpiresAt(),
                entidad.getUsedAt()));
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        tokens.save(new PasswordResetTokenEntity(
                token.id(),
                token.userId().value(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt(),
                token.usedAt()));
        return token;
    }

    @Override
    public void invalidateAllForUser(UserId userId, Instant now) {
        tokens.invalidateAllForUser(userId.value(), now);
    }
}
