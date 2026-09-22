package com.gastos.iam.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code user_credential}.
 *
 * <p>Tabla aparte de {@code app_user} a proposito: la mayoria de consultas del sistema
 * leen usuarios, y el hash no tiene por que viajar en ninguna de ellas. Separarlo reduce
 * las probabilidades de que acabe donde no debe.</p>
 */
@Entity
@Table(name = "user_credential")
public class UserCredentialEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserCredentialEntity() {
        // Requerido por JPA.
    }

    public UserCredentialEntity(UUID userId, String passwordHash, Instant updatedAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
