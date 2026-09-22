package com.gastos.iam.infrastructure.persistence.jpa;

import com.gastos.iam.domain.model.PasskeyCeremony;
import com.gastos.iam.domain.model.PasskeyChallenge;
import com.gastos.iam.domain.model.PasskeyCredential;
import com.gastos.iam.domain.port.PasskeyChallengeRepository;
import com.gastos.iam.domain.port.PasskeyCredentialRepository;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptadores de salida de las passkeys.
 *
 * <p>Los dos viven en el mismo fichero porque comparten la conversion de base64 y se leen
 * mejor juntos; son clases de primer nivel, no anidadas, para que Spring Data las
 * descubra.</p>
 */
public final class PasskeyRepositoryAdapters {

    private PasskeyRepositoryAdapters() {
    }

    /** Codificacion del material criptografico: ver el comentario de la entidad. */
    static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] decode(String base64) {
        return Base64.getUrlDecoder().decode(base64);
    }
}

@Repository
@Transactional
class PasskeyCredentialRepositoryAdapter implements PasskeyCredentialRepository {

    private final PasskeyCredentialJpaRepository credentials;

    PasskeyCredentialRepositoryAdapter(PasskeyCredentialJpaRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasskeyCredential> findByCredentialId(String credentialId) {
        return credentials.findByCredentialId(credentialId)
                .map(PasskeyCredentialRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasskeyCredential> findById(UUID id) {
        return credentials.findById(id).map(PasskeyCredentialRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PasskeyCredential> findAllByUser(UserId userId) {
        return credentials.findAllByUserIdOrderByCreatedAtAsc(userId.value()).stream()
                .map(PasskeyCredentialRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public PasskeyCredential save(PasskeyCredential credential) {
        credentials.save(new PasskeyCredentialEntity(
                credential.id(),
                credential.userId().value(),
                credential.credentialId(),
                PasskeyRepositoryAdapters.encode(credential.attestedCredentialData()),
                credential.signatureCount(),
                credential.backupEligible(),
                credential.label(),
                credential.createdAt(),
                credential.lastUsedAt()));
        return credential;
    }

    @Override
    public void delete(UUID id) {
        credentials.deleteById(id);
    }

    private static PasskeyCredential toDomain(PasskeyCredentialEntity entidad) {
        return PasskeyCredential.rehydrate(
                entidad.getId(),
                new UserId(entidad.getUserId()),
                entidad.getCredentialId(),
                PasskeyRepositoryAdapters.decode(entidad.getAttestedCredentialData()),
                entidad.getSignatureCount(),
                entidad.isBackupEligible(),
                entidad.getLabel(),
                entidad.getCreatedAt(),
                entidad.getLastUsedAt());
    }
}

@Repository
@Transactional
class PasskeyChallengeRepositoryAdapter implements PasskeyChallengeRepository {

    private final PasskeyChallengeJpaRepository challenges;

    PasskeyChallengeRepositoryAdapter(PasskeyChallengeJpaRepository challenges) {
        this.challenges = challenges;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasskeyChallenge> findByChallenge(String challenge) {
        return challenges.findByChallenge(challenge).map(entidad -> PasskeyChallenge.rehydrate(
                entidad.getId(),
                entidad.getChallenge(),
                PasskeyCeremony.valueOf(entidad.getCeremony()),
                entidad.getUserId() == null ? null : new UserId(entidad.getUserId()),
                entidad.getIssuedAt(),
                entidad.getExpiresAt(),
                entidad.getUsedAt()));
    }

    @Override
    public PasskeyChallenge save(PasskeyChallenge challenge) {
        challenges.save(new PasskeyChallengeEntity(
                challenge.id(),
                challenge.challenge(),
                challenge.ceremony().name(),
                challenge.userId() == null ? null : challenge.userId().value(),
                challenge.issuedAt(),
                challenge.expiresAt(),
                challenge.usedAt()));
        return challenge;
    }

    @Override
    public int deleteExpired(Instant now) {
        return challenges.deleteExpired(now);
    }
}
