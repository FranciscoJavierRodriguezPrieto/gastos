package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.HouseholdInvitation;
import com.gastos.shared.domain.HouseholdId;
import java.time.Instant;
import java.util.Optional;

/** Puerto de salida de las invitaciones al hogar. */
public interface HouseholdInvitationRepository {

    /** Busqueda por hash: el codigo en claro nunca llega a la base de datos. */
    Optional<HouseholdInvitation> findByCodeHash(String codeHash);

    /** La invitacion vigente del hogar, si la hay. Como mucho puede haber una. */
    Optional<HouseholdInvitation> findActiveByHousehold(HouseholdId householdId, Instant now);

    HouseholdInvitation save(HouseholdInvitation invitation);

    /**
     * Revoca las invitaciones vivas del hogar.
     *
     * <p>Se llama al emitir una nueva y al aceptar una: generar un codigo debe dejar
     * exactamente uno en circulacion, y entrar en el hogar no debe dejar ninguno.</p>
     */
    void revokeAllForHousehold(HouseholdId householdId, Instant now);
}
