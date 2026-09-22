package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Passkey registrada por un usuario.
 *
 * <p>A diferencia de una contrasena, aqui <strong>no hay secreto que guardar</strong>: lo
 * que se almacena es la clave publica. Quien lea esta tabla entera no puede suplantar a
 * nadie, porque la clave privada no sale nunca del dispositivo. Esa es la razon de fondo
 * para ofrecer passkeys en una aplicacion con datos financieros, y no solo la comodidad
 * de entrar con la huella.</p>
 *
 * <p>El material criptografico se guarda tal y como lo serializa la libreria de
 * verificacion, en {@code attestedCredentialData}. El dominio lo trata como una caja
 * opaca a proposito: interpretarlo es cosa del adaptador, no del modelo.</p>
 */
public final class PasskeyCredential {

    private final UUID id;
    private final UserId userId;
    /** Identificador que el autenticador asigna a la credencial, en base64url. */
    private final String credentialId;
    private final byte[] attestedCredentialData;
    private long signatureCount;
    private final boolean backupEligible;
    private String label;
    private final Instant createdAt;
    private Instant lastUsedAt;

    private PasskeyCredential(UUID id, UserId userId, String credentialId,
                              byte[] attestedCredentialData, long signatureCount, boolean backupEligible,
                              String label, Instant createdAt, Instant lastUsedAt) {
        this.id = Guard.notNull(id, "id");
        this.userId = Guard.notNull(userId, "userId");
        this.credentialId = Guard.notBlank(credentialId, "credentialId");
        this.attestedCredentialData = requireMaterial(attestedCredentialData);
        this.signatureCount = requireNonNegative(signatureCount);
        this.backupEligible = backupEligible;
        this.label = Guard.notBlank(label, "label");
        this.createdAt = Guard.notNull(createdAt, "createdAt");
        this.lastUsedAt = lastUsedAt;
    }

    public static PasskeyCredential register(UserId userId, String credentialId,
                                             byte[] attestedCredentialData, long signatureCount,
                                             boolean backupEligible, String label, Instant now) {
        return new PasskeyCredential(UUID.randomUUID(), userId, credentialId,
                attestedCredentialData, signatureCount, backupEligible, label, now, null);
    }

    public static PasskeyCredential rehydrate(UUID id, UserId userId, String credentialId,
                                              byte[] attestedCredentialData, long signatureCount,
                                              boolean backupEligible, String label, Instant createdAt,
                                              Instant lastUsedAt) {
        return new PasskeyCredential(id, userId, credentialId, attestedCredentialData,
                signatureCount, backupEligible, label, createdAt, lastUsedAt);
    }

    /**
     * Anota un uso correcto y avanza el contador de firmas.
     *
     * <p>El contador sirve para <strong>detectar credenciales clonadas</strong>: un
     * autenticador de verdad solo lo incrementa. Si vuelve un valor que ya se habia
     * visto, o uno anterior, lo mas probable es que se haya copiado el dispositivo.</p>
     *
     * <p>Un contador que se queda en cero no es sospechoso: las passkeys sincronizadas
     * entre dispositivos —las que usan casi todos— no lo llevan, precisamente porque
     * viven en varios sitios a la vez. Por eso solo se comprueba cuando el autenticador
     * demuestra que si lo usa.</p>
     */
    public void recordUse(long newSignatureCount, Instant now) {
        Guard.notNull(now, "now");
        requireNonNegative(newSignatureCount);

        boolean elAutenticadorLlevaCuenta = signatureCount > 0 || newSignatureCount > 0;
        if (elAutenticadorLlevaCuenta && newSignatureCount <= signatureCount) {
            throw new ClonedCredentialException();
        }

        this.signatureCount = newSignatureCount;
        this.lastUsedAt = now;
    }

    public void rename(String newLabel) {
        this.label = Guard.notBlank(newLabel, "label");
    }

    public boolean belongsTo(UserId candidate) {
        return userId.equals(candidate);
    }

    private static byte[] requireMaterial(byte[] material) {
        if (material == null || material.length == 0) {
            throw new DomainException("La passkey no trae material criptografico");
        }
        return material.clone();
    }

    private static long requireNonNegative(long count) {
        if (count < 0) {
            throw new DomainException("El contador de firmas no puede ser negativo");
        }
        return count;
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String credentialId() {
        return credentialId;
    }

    public byte[] attestedCredentialData() {
        return attestedCredentialData.clone();
    }

    public long signatureCount() {
        return signatureCount;
    }

    public boolean backupEligible() {
        return backupEligible;
    }

    public String label() {
        return label;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    @Override
    public String toString() {
        return "PasskeyCredential[id=" + id + ", userId=" + userId + ", label=" + label + "]";
    }

    /** Contador de firmas que no avanza: la credencial puede estar clonada. */
    public static class ClonedCredentialException extends RuntimeException {
        public ClonedCredentialException() {
            super("Esta passkey parece haber sido clonada");
        }
    }
}
