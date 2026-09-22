package com.gastos.iam.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de {@code passkey_credential}.
 *
 * <p>El material criptografico se guarda como texto en base64url y no como binario a
 * proposito. PostgreSQL lo escribiria en {@code bytea} y H2 en {@code varbinary}, y ese
 * es justo el tipo de divergencia que haria que los tests dejaran de validar el esquema
 * real. El coste es un tercio mas de espacio sobre unos cientos de bytes.</p>
 */
@Entity
@Table(name = "passkey_credential")
public class PasskeyCredentialEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", nullable = false, length = 512, unique = true)
    private String credentialId;

    @Column(name = "attested_credential_data", nullable = false, length = 2048)
    private String attestedCredentialData;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected PasskeyCredentialEntity() {
        // Requerido por JPA.
    }

    public PasskeyCredentialEntity(UUID id, UUID userId, String credentialId,
                                   String attestedCredentialData, long signatureCount,
                                   boolean backupEligible, String label, Instant createdAt,
                                   Instant lastUsedAt) {
        this.id = id;
        this.userId = userId;
        this.credentialId = credentialId;
        this.attestedCredentialData = attestedCredentialData;
        this.signatureCount = signatureCount;
        this.backupEligible = backupEligible;
        this.label = label;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getAttestedCredentialData() {
        return attestedCredentialData;
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public boolean isBackupEligible() {
        return backupEligible;
    }

    public String getLabel() {
        return label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
