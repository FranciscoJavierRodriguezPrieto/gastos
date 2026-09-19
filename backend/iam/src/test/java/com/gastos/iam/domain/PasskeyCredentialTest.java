package com.gastos.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.iam.domain.model.PasskeyCredential;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Contador de firmas de una passkey")
class PasskeyCredentialTest {

    private static final Instant AHORA = Instant.parse("2026-03-01T10:00:00Z");
    private static final byte[] MATERIAL = new byte[] {1, 2, 3, 4};

    private static PasskeyCredential conContador(long contador) {
        return PasskeyCredential.register(UserId.newId(), "credencial-1", MATERIAL, contador,
                true, "iPhone", AHORA);
    }

    @Test
    @DisplayName("un contador que avanza se acepta y sella la fecha de uso")
    void acceptsIncreasingCounter() {
        PasskeyCredential credencial = conContador(5);

        credencial.recordUse(6, AHORA.plusSeconds(60));

        assertThat(credencial.signatureCount()).isEqualTo(6);
        assertThat(credencial.lastUsedAt()).isEqualTo(AHORA.plusSeconds(60));
    }

    @Test
    @DisplayName("un contador que retrocede delata una credencial clonada")
    void rejectsDecreasingCounter() {
        PasskeyCredential credencial = conContador(5);

        assertThatThrownBy(() -> credencial.recordUse(4, AHORA))
                .isInstanceOf(PasskeyCredential.ClonedCredentialException.class);
    }

    @Test
    @DisplayName("repetir el mismo contador tambien es sospechoso")
    void rejectsRepeatedCounter() {
        PasskeyCredential credencial = conContador(5);

        assertThatThrownBy(() -> credencial.recordUse(5, AHORA))
                .isInstanceOf(PasskeyCredential.ClonedCredentialException.class);
    }

    /**
     * El caso importante: las passkeys sincronizadas —las de Apple, Google o un gestor de
     * contrasenas— dejan el contador a cero siempre. Si esto fallara, la forma mas comun
     * de passkey seria inutilizable a partir del segundo acceso.
     */
    @Test
    @DisplayName("un autenticador que no lleva cuenta se queda en cero sin levantar sospechas")
    void allowsAuthenticatorsWithoutCounter() {
        PasskeyCredential credencial = conContador(0);

        credencial.recordUse(0, AHORA);
        credencial.recordUse(0, AHORA.plusSeconds(3600));

        assertThat(credencial.signatureCount()).isZero();
        assertThat(credencial.lastUsedAt()).isEqualTo(AHORA.plusSeconds(3600));
    }

    @Test
    @DisplayName("el material criptografico se copia al entrar y al salir del agregado")
    void doesNotLeakInternalArray() {
        byte[] original = {9, 9, 9};
        PasskeyCredential credencial = PasskeyCredential.register(UserId.newId(), "c", original, 0,
                false, "Llave", AHORA);

        original[0] = 0;
        credencial.attestedCredentialData()[1] = 0;

        assertThat(credencial.attestedCredentialData()).containsExactly(9, 9, 9);
    }

    @Test
    @DisplayName("la pertenencia se comprueba contra el propietario, no contra el hogar")
    void checksOwnership() {
        UserId duenno = UserId.newId();
        PasskeyCredential credencial = PasskeyCredential.register(duenno, "c", MATERIAL, 0, false,
                "Llave", AHORA);

        assertThat(credencial.belongsTo(duenno)).isTrue();
        assertThat(credencial.belongsTo(UserId.newId())).isFalse();
    }
}
