package com.gastos.iam.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Fila de {@code household_invitation}. Guarda el hash, nunca el codigo que se reparte. */
@Entity
@Table(name = "household_invitation")
public class HouseholdInvitationEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "code_hash", nullable = false, length = 64, unique = true)
    private String codeHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected HouseholdInvitationEntity() {
        // Requerido por JPA.
    }

    public HouseholdInvitationEntity(UUID id, UUID householdId, UUID invitedBy, String codeHash,
                                     Instant issuedAt, Instant expiresAt, Instant acceptedAt,
                                     Instant revokedAt) {
        this.id = id;
        this.householdId = householdId;
        this.invitedBy = invitedBy;
        this.codeHash = codeHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
