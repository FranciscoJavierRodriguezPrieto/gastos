package com.gastos.iam.infrastructure.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de las invitaciones al hogar. */
public interface HouseholdInvitationJpaRepository
        extends JpaRepository<HouseholdInvitationEntity, UUID> {

    Optional<HouseholdInvitationEntity> findByCodeHash(String codeHash);

    /**
     * La invitacion viva del hogar. Puede haber filas antiguas aceptadas o revocadas,
     * pero vigente solo puede haber una: emitir revoca las anteriores.
     */
    @Query("""
            select i from HouseholdInvitationEntity i
             where i.householdId = :householdId
               and i.acceptedAt is null
               and i.revokedAt is null
               and i.expiresAt > :now
            """)
    Optional<HouseholdInvitationEntity> findActive(@Param("householdId") UUID householdId,
                                                   @Param("now") Instant now);

    /** Revoca las invitaciones vivas del hogar en una sola sentencia. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update HouseholdInvitationEntity i
               set i.revokedAt = :now
             where i.householdId = :householdId
               and i.acceptedAt is null
               and i.revokedAt is null
            """)
    int revokeAllForHousehold(@Param("householdId") UUID householdId, @Param("now") Instant now);
}
