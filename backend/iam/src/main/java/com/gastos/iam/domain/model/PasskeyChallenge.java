package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Reto de una ceremonia WebAuthn.
 *
 * <p>El reto vive en la base de datos y no en una sesion de servidor porque la API es sin
 * estado: no hay sesion donde guardarlo. La contrapartida es una tabla mas, y a cambio la
 * aplicacion sigue pudiendo escalar a varias instancias sin sesiones pegajosas.</p>
 *
 * <p>Se guarda <strong>en claro y no su hash</strong>, al contrario que los tokens de
 * refresco o de restablecimiento. No es una credencial: es un numero aleatorio que el
 * cliente tiene que devolver firmado. Conocerlo no sirve de nada sin la clave privada, y
 * en cambio el servidor necesita el valor original para compararlo con el que viene
 * firmado.</p>
 *
 * <p>Un solo uso y vida muy corta: el reto es lo unico que impide repetir una respuesta
 * capturada.</p>
 */
public final class PasskeyChallenge {

    private final UUID id;
    /** Valor aleatorio en base64url, tal y como viaja al navegador. */
    private final String challenge;
    private final PasskeyCeremony ceremony;
    /** Quien lo pidio; nulo en el acceso, donde todavia no se sabe quien llama. */
    private final UserId userId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private PasskeyChallenge(UUID id, String challenge, PasskeyCeremony ceremony, UserId userId,
                             Instant issuedAt, Instant expiresAt, Instant usedAt) {
        this.id = Guard.notNull(id, "id");
        this.challenge = Guard.notBlank(challenge, "challenge");
        this.ceremony = Guard.notNull(ceremony, "ceremony");
        this.userId = userId;
        this.issuedAt = Guard.notNull(issuedAt, "issuedAt");
        this.expiresAt = Guard.notNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new DomainException("La caducidad debe ser posterior a la emision");
        }
        this.usedAt = usedAt;
    }

    public static PasskeyChallenge issue(String challenge, PasskeyCeremony ceremony, UserId userId,
                                         Instant now, Instant expiresAt) {
        return new PasskeyChallenge(UUID.randomUUID(), challenge, ceremony, userId, now,
                expiresAt, null);
    }

    public static PasskeyChallenge rehydrate(UUID id, String challenge, PasskeyCeremony ceremony,
                                             UserId userId, Instant issuedAt, Instant expiresAt,
                                             Instant usedAt) {
        return new PasskeyChallenge(id, challenge, ceremony, userId, issuedAt, expiresAt, usedAt);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** Un reto sirve para lo que se pidio y para quien lo pidio, no para otra cosa. */
    public boolean matches(PasskeyCeremony expected, UserId expectedUser) {
        if (ceremony != expected) {
            return false;
        }
        return expectedUser == null ? userId == null : expectedUser.equals(userId);
    }

    public void consume(Instant now) {
        if (usedAt != null) {
            throw new DomainException("Este reto ya se ha utilizado");
        }
        this.usedAt = Guard.notNull(now, "now");
    }

    public UUID id() {
        return id;
    }

    public String challenge() {
        return challenge;
    }

    public PasskeyCeremony ceremony() {
        return ceremony;
    }

    public UserId userId() {
        return userId;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    @Override
    public String toString() {
        return "PasskeyChallenge[id=" + id + ", ceremonia=" + ceremony
                + ", usado=" + (usedAt != null) + "]";
    }
}
