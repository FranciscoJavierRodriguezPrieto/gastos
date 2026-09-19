package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Token de restablecimiento de contrasena.
 *
 * <p>Se guarda el <strong>hash</strong>, igual que con los tokens de refresco: quien lea
 * la base de datos no obtiene nada utilizable.</p>
 *
 * <p>Vida corta y un solo uso. Es una credencial que llega por correo, un canal que no
 * controlamos: cuanto menos tiempo sea valida, menos ventana hay si el buzon esta
 * comprometido o si el mensaje se reenvia sin pensar.</p>
 */
public final class PasswordResetToken {

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private PasswordResetToken(UUID id, UserId userId, String tokenHash, Instant issuedAt,
                               Instant expiresAt, Instant usedAt) {
        this.id = Guard.notNull(id, "id");
        this.userId = Guard.notNull(userId, "userId");
        this.tokenHash = Guard.notBlank(tokenHash, "tokenHash");
        this.issuedAt = Guard.notNull(issuedAt, "issuedAt");
        this.expiresAt = Guard.notNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new DomainException("La caducidad debe ser posterior a la emision");
        }
        this.usedAt = usedAt;
    }

    public static PasswordResetToken issue(UserId userId, String tokenHash, Instant now,
                                           Instant expiresAt) {
        return new PasswordResetToken(UUID.randomUUID(), userId, tokenHash, now, expiresAt, null);
    }

    public static PasswordResetToken rehydrate(UUID id, UserId userId, String tokenHash,
                                               Instant issuedAt, Instant expiresAt, Instant usedAt) {
        return new PasswordResetToken(id, userId, tokenHash, issuedAt, expiresAt, usedAt);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        if (usedAt != null) {
            throw new DomainException("Este enlace ya se ha utilizado");
        }
        this.usedAt = Guard.notNull(now, "now");
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

    public Instant usedAt() {
        return usedAt;
    }

    @Override
    public String toString() {
        return "PasswordResetToken[id=" + id + ", userId=" + userId + ", usado=" + (usedAt != null) + "]";
    }
}
