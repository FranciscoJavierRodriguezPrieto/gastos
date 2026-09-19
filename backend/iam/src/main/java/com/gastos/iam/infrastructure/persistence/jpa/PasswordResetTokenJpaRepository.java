package com.gastos.iam.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de los tokens de restablecimiento. */
public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetTokenEntity, UUID> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    /** Marca como usados todos los tokens vivos de un usuario, en una sola sentencia. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetTokenEntity t
               set t.usedAt = :now
             where t.userId = :userId
               and t.usedAt is null
            """)
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
