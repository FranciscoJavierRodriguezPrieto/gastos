package com.gastos.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.iam.domain.model.PasskeyCeremony;
import com.gastos.iam.domain.model.PasskeyChallenge;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reto de una ceremonia WebAuthn")
class PasskeyChallengeTest {

    private static final Instant AHORA = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant CADUCA = AHORA.plusSeconds(300);

    @Test
    @DisplayName("caducado deja de servir")
    void expires() {
        PasskeyChallenge reto = PasskeyChallenge.issue("reto", PasskeyCeremony.ACCESO, null,
                AHORA, CADUCA);

        assertThat(reto.isUsable(CADUCA.minusSeconds(1))).isTrue();
        assertThat(reto.isUsable(CADUCA)).isFalse();
    }

    @Test
    @DisplayName("usado no vuelve a servir")
    void isSingleUse() {
        PasskeyChallenge reto = PasskeyChallenge.issue("reto", PasskeyCeremony.ACCESO, null,
                AHORA, CADUCA);

        reto.consume(AHORA);

        assertThat(reto.isUsable(AHORA)).isFalse();
        assertThatThrownBy(() -> reto.consume(AHORA)).isInstanceOf(DomainException.class);
    }

    /**
     * Sin esta comprobacion, un reto conseguido en el alta —que exige sesion— se podria
     * presentar en el acceso, que es publico.
     */
    @Test
    @DisplayName("un reto de alta no vale para acceder")
    void doesNotCrossCeremonies() {
        UserId usuario = UserId.newId();
        PasskeyChallenge reto = PasskeyChallenge.issue("reto", PasskeyCeremony.REGISTRO, usuario,
                AHORA, CADUCA);

        assertThat(reto.matches(PasskeyCeremony.REGISTRO, usuario)).isTrue();
        assertThat(reto.matches(PasskeyCeremony.ACCESO, null)).isFalse();
    }

    @Test
    @DisplayName("un reto de alta de otro usuario no vale")
    void doesNotCrossUsers() {
        PasskeyChallenge reto = PasskeyChallenge.issue("reto", PasskeyCeremony.REGISTRO,
                UserId.newId(), AHORA, CADUCA);

        assertThat(reto.matches(PasskeyCeremony.REGISTRO, UserId.newId())).isFalse();
    }

    @Test
    @DisplayName("un reto de acceso no lleva usuario, y eso tambien se comprueba")
    void anonymousChallengeHasNoUser() {
        PasskeyChallenge reto = PasskeyChallenge.issue("reto", PasskeyCeremony.ACCESO, null,
                AHORA, CADUCA);

        assertThat(reto.matches(PasskeyCeremony.ACCESO, null)).isTrue();
        assertThat(reto.matches(PasskeyCeremony.ACCESO, UserId.newId())).isFalse();
    }
}
