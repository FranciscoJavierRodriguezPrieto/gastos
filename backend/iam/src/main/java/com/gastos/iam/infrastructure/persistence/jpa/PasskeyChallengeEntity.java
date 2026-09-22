package com.gastos.iam.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code passkey_challenge}. Sustituye a la sesion de servidor que no existe. */
@Entity
@Table(name = "passkey_challenge")
public class PasskeyChallengeEntity {

    @Id
    private UUID id;

    @Column(name = "challenge", nullable = false, length = 128, unique = true)
    private String challenge;

    @Column(name = "ceremony", nullable = false, length = 20)
    private String ceremony;

    /** Nulo en el acceso: el reto se emite antes de saber quien va a firmarlo. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasskeyChallengeEntity() {
        // Requerido por JPA.
    }

    public PasskeyChallengeEntity(UUID id, String challenge, String ceremony, UUID userId,
                                  Instant issuedAt, Instant expiresAt, Instant usedAt) {
        this.id = id;
        this.challenge = challenge;
        this.ceremony = ceremony;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getChallenge() {
        return challenge;
    }

    public String getCeremony() {
        return ceremony;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
