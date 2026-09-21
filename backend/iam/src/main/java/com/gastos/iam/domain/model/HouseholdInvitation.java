package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Invitacion para que la pareja se de de alta ella misma.
 *
 * <p>Sustituye al alta directa en la que el titular elegia la contrasena del otro
 * conviviente. Aquella tenia un defecto que ninguna validacion arregla: <strong>alguien
 * conocia una contrasena que no era suya</strong>, y encima la primera que se usaba para
 * entrar. Con la invitacion, el titular entrega un codigo de un solo uso y es la pareja
 * quien elige su contrasena, que nadie mas llega a ver.</p>
 *
 * <p>Como con los tokens de refresco y los de restablecimiento, <strong>se guarda el
 * hash del codigo y no el codigo</strong>. Tiene una consecuencia visible en la
 * interfaz, y es intencionada: el codigo se ensena <em>una sola vez</em>, al generarlo.
 * Quien lo pierda genera otro, lo cual invalida el anterior. Guardarlo en claro para
 * poder volver a ensenarlo convertiria la tabla en una lista de llaves del hogar.</p>
 *
 * <p>Vida corta, un solo uso y revocable. Es una credencial que viaja por WhatsApp o por
 * donde sea: no controlamos el canal, asi que lo que se controla es la ventana.</p>
 */
public final class HouseholdInvitation {

    private final UUID id;
    private final HouseholdId householdId;
    private final UserId invitedBy;
    private final String codeHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant acceptedAt;
    private Instant revokedAt;

    private HouseholdInvitation(UUID id, HouseholdId householdId, UserId invitedBy, String codeHash,
                                Instant issuedAt, Instant expiresAt, Instant acceptedAt,
                                Instant revokedAt) {
        this.id = Guard.notNull(id, "id");
        this.householdId = Guard.notNull(householdId, "householdId");
        this.invitedBy = Guard.notNull(invitedBy, "invitedBy");
        this.codeHash = Guard.notBlank(codeHash, "codeHash");
        this.issuedAt = Guard.notNull(issuedAt, "issuedAt");
        this.expiresAt = Guard.notNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new DomainException("La caducidad debe ser posterior a la emision");
        }
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
    }

    public static HouseholdInvitation issue(HouseholdId householdId, UserId invitedBy,
                                            String codeHash, Instant now, Instant expiresAt) {
        return new HouseholdInvitation(UUID.randomUUID(), householdId, invitedBy, codeHash, now,
                expiresAt, null, null);
    }

    public static HouseholdInvitation rehydrate(UUID id, HouseholdId householdId, UserId invitedBy,
                                                String codeHash, Instant issuedAt,
                                                Instant expiresAt, Instant acceptedAt,
                                                Instant revokedAt) {
        return new HouseholdInvitation(id, householdId, invitedBy, codeHash, issuedAt, expiresAt,
                acceptedAt, revokedAt);
    }

    /** Vigente: ni aceptada, ni revocada, ni caducada. */
    public boolean isUsable(Instant now) {
        Guard.notNull(now, "now");
        return acceptedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Marca la invitacion como usada.
     *
     * <p>Falla si ya no es utilizable en lugar de no hacer nada: quien la acepta esta a
     * punto de crear una cuenta en el hogar, y eso no puede depender de que el llamante
     * se acuerde de comprobarlo antes.</p>
     */
    public void accept(Instant now) {
        Guard.notNull(now, "now");
        if (!isUsable(now)) {
            throw new DomainException("Esta invitacion ya no es valida");
        }
        this.acceptedAt = now;
    }

    /** Revocar una invitacion ya usada no tiene efecto: la cuenta ya existe. */
    public void revoke(Instant now) {
        Guard.notNull(now, "now");
        if (acceptedAt != null) {
            throw new DomainException("Esta invitacion ya se ha aceptado y no se puede revocar");
        }
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public HouseholdId householdId() {
        return householdId;
    }

    public UserId invitedBy() {
        return invitedBy;
    }

    public String codeHash() {
        return codeHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    @Override
    public String toString() {
        return "HouseholdInvitation[id=" + id + ", hogar=" + householdId
                + ", aceptada=" + (acceptedAt != null) + ", revocada=" + (revokedAt != null) + "]";
    }
}
