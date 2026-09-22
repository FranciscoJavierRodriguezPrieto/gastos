package com.gastos.iam.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.UserId;
import java.time.Instant;

/**
 * Credencial de acceso de un usuario.
 *
 * <p>El dominio guarda un <strong>hash opaco</strong> y nunca la contrasena. Ni siquiera
 * sabe con que algoritmo esta calculado: cifrar y verificar son responsabilidad del
 * puerto {@code PasswordHasher}, cuya implementacion vive en infraestructura.</p>
 *
 * <p>Esto no es purismo. Significa que la contrasena en claro existe solo dentro del
 * caso de uso, durante microsegundos, y jamas puede acabar en una traza, en una
 * serializacion accidental ni en un volcado de memoria de un agregado.</p>
 */
public final class UserCredential {

    private final UserId userId;
    private String passwordHash;
    private Instant updatedAt;

    private UserCredential(UserId userId, String passwordHash, Instant updatedAt) {
        this.userId = Guard.notNull(userId, "userId");
        this.passwordHash = Guard.notBlank(passwordHash, "passwordHash");
        this.updatedAt = Guard.notNull(updatedAt, "updatedAt");
    }

    public static UserCredential of(UserId userId, String passwordHash, Instant now) {
        return new UserCredential(userId, passwordHash, now);
    }

    public void changeTo(String newPasswordHash, Instant now) {
        this.passwordHash = Guard.notBlank(newPasswordHash, "passwordHash");
        this.updatedAt = Guard.notNull(now, "now");
    }

    public UserId userId() {
        return userId;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Sin el hash: este objeto no aparece nunca entero en una traza. */
    @Override
    public String toString() {
        return "UserCredential[userId=" + userId + "]";
    }
}
