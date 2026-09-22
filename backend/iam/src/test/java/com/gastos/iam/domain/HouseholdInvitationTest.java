package com.gastos.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.iam.domain.model.HouseholdInvitation;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Invitacion al hogar")
class HouseholdInvitationTest {

    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");
    private static final Instant CADUCA = AHORA.plus(Duration.ofDays(7));

    private HouseholdInvitation nueva() {
        return HouseholdInvitation.issue(HouseholdId.newId(), UserId.newId(), "hash", AHORA,
                CADUCA);
    }

    @Test
    @DisplayName("recien emitida es utilizable")
    void freshOneIsUsable() {
        assertThat(nueva().isUsable(AHORA)).isTrue();
    }

    @Test
    @DisplayName("deja de valer cuando caduca")
    void expires() {
        HouseholdInvitation invitacion = nueva();
        assertThat(invitacion.isUsable(CADUCA.minusSeconds(1))).isTrue();
        assertThat(invitacion.isUsable(CADUCA)).isFalse();
        assertThat(invitacion.isUsable(CADUCA.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("aceptarla la consume: el codigo es de un solo uso")
    void acceptingConsumesIt() {
        HouseholdInvitation invitacion = nueva();
        invitacion.accept(AHORA);

        assertThat(invitacion.acceptedAt()).isEqualTo(AHORA);
        assertThat(invitacion.isUsable(AHORA)).isFalse();
    }

    /**
     * Que falle en vez de no hacer nada es lo importante: quien la acepta esta creando
     * una cuenta en el hogar, y eso no puede depender de que el llamante se acuerde de
     * comprobar antes si seguia viva.
     */
    @Test
    @DisplayName("aceptar dos veces falla")
    void cannotAcceptTwice() {
        HouseholdInvitation invitacion = nueva();
        invitacion.accept(AHORA);

        assertThatThrownBy(() -> invitacion.accept(AHORA))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("ya no es valida");
    }

    @Test
    @DisplayName("aceptar una caducada falla")
    void cannotAcceptExpired() {
        HouseholdInvitation invitacion = nueva();

        assertThatThrownBy(() -> invitacion.accept(CADUCA.plusSeconds(1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("revocarla la invalida, y revocar dos veces no cambia nada")
    void revoking() {
        HouseholdInvitation invitacion = nueva();
        invitacion.revoke(AHORA);
        Instant primeraRevocacion = invitacion.revokedAt();

        invitacion.revoke(AHORA.plusSeconds(60));

        assertThat(invitacion.isUsable(AHORA)).isFalse();
        assertThat(invitacion.revokedAt()).isEqualTo(primeraRevocacion);
    }

    @Test
    @DisplayName("una invitacion ya aceptada no se puede revocar")
    void cannotRevokeAccepted() {
        HouseholdInvitation invitacion = nueva();
        invitacion.accept(AHORA);

        // La cuenta ya existe: revocar daria a entender que se deshace, y no se deshace.
        assertThatThrownBy(() -> invitacion.revoke(AHORA))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("ya se ha aceptado");
    }

    @Test
    @DisplayName("una caducidad anterior a la emision se rechaza")
    void expiryMustFollowIssue() {
        assertThatThrownBy(() -> HouseholdInvitation.issue(HouseholdId.newId(), UserId.newId(),
                "hash", AHORA, AHORA.minusSeconds(1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("no delata nada al convertirla en texto")
    void toStringIsSafe() {
        assertThat(nueva().toString()).doesNotContain("hash");
    }
}
