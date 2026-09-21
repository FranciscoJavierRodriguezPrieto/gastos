package com.gastos.iam.infrastructure.persistence.jpa;

import com.gastos.iam.domain.model.HouseholdInvitation;
import com.gastos.iam.domain.port.HouseholdInvitationRepository;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida de las invitaciones al hogar. */
@Repository
@Transactional
public class HouseholdInvitationRepositoryAdapter implements HouseholdInvitationRepository {

    private final HouseholdInvitationJpaRepository invitations;

    public HouseholdInvitationRepositoryAdapter(HouseholdInvitationJpaRepository invitations) {
        this.invitations = invitations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HouseholdInvitation> findByCodeHash(String codeHash) {
        return invitations.findByCodeHash(codeHash).map(HouseholdInvitationRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HouseholdInvitation> findActiveByHousehold(HouseholdId householdId, Instant now) {
        return invitations.findActive(householdId.value(), now)
                .map(HouseholdInvitationRepositoryAdapter::toDomain);
    }

    @Override
    public HouseholdInvitation save(HouseholdInvitation invitation) {
        invitations.save(new HouseholdInvitationEntity(
                invitation.id(),
                invitation.householdId().value(),
                invitation.invitedBy().value(),
                invitation.codeHash(),
                invitation.issuedAt(),
                invitation.expiresAt(),
                invitation.acceptedAt(),
                invitation.revokedAt()));
        return invitation;
    }

    @Override
    public void revokeAllForHousehold(HouseholdId householdId, Instant now) {
        invitations.revokeAllForHousehold(householdId.value(), now);
    }

    private static HouseholdInvitation toDomain(HouseholdInvitationEntity entidad) {
        return HouseholdInvitation.rehydrate(
                entidad.getId(),
                new HouseholdId(entidad.getHouseholdId()),
                new UserId(entidad.getInvitedBy()),
                entidad.getCodeHash(),
                entidad.getIssuedAt(),
                entidad.getExpiresAt(),
                entidad.getAcceptedAt(),
                entidad.getRevokedAt());
    }
}
