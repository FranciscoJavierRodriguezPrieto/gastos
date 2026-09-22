package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Token de refresco con rotacion.
 *
 * <p>Se guarda el <strong>hash</strong> del token, no el token: si alguien lee la base
 * de datos no obtiene credenciales utilizables, igual que con las contrasenas.</p>
 *
 * <p>Rotacion: cada uso consume el token y emite uno nuevo. Si un token ya consumido
 * vuelve a presentarse, es senal de que ha sido robado y se revoca toda la cadena. Por
 * eso se guarda {@code replacedBy}: permite seguir el rastro hasta el token vivo.</p>
 */
public final class RefreshToken {

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private UUID replacedBy;

    private RefreshToken(UUID id, UserId userId, String tokenHash, Instant issuedAt,
                         Instant expiresAt, Instant revokedAt, UUID replacedBy) {
        this.id = Guard.notNull(id, "id");
        this.userId = Guard.notNull(userId, "userId");
        this.tokenHash = Guard.notBlank(tokenHash, "tokenHash");
        this.issuedAt = Guard.notNull(issuedAt, "issuedAt");
        this.expiresAt = Guard.notNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new DomainException("La caducidad del token debe ser posterior a su emision");
        }
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
    }

    public static RefreshToken issue(UserId userId, String tokenHash, Instant now, Instant expiresAt) {
        return new RefreshToken(UUID.randomUUID(), userId, tokenHash, now, expiresAt, null, null);
    }

    public static RefreshToken rehydrate(UUID id, UserId userId, String tokenHash, Instant issuedAt,
                                         Instant expiresAt, Instant revokedAt, UUID replacedBy) {
        return new RefreshToken(id, userId, tokenHash, issuedAt, expiresAt, revokedAt, replacedBy);
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Consume el token al rotarlo y deja constancia de cual lo sustituye. */
    public void rotateTo(RefreshToken replacement, Instant now) {
        Guard.notNull(replacement, "replacement");
        revoke(now);
        this.replacedBy = replacement.id();
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = Guard.notNull(now, "now");
        }
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public UUID replacedBy() {
        return replacedBy;
    }

    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", userId=" + userId + ", revoked=" + isRevoked() + "]";
    }
}
