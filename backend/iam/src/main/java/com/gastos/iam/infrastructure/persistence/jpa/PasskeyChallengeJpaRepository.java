package com.gastos.iam.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de los retos WebAuthn. */
public interface PasskeyChallengeJpaRepository extends JpaRepository<PasskeyChallengeEntity, UUID> {

    Optional<PasskeyChallengeEntity> findByChallenge(String challenge);

    /** Barrido de retos caducados; se lanza al emitir uno nuevo. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasskeyChallengeEntity c where c.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
