package com.gastos.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.iam.domain.model.InvitationCode;
import com.gastos.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Codigo de invitacion")
class InvitationCodeTest {

    private static final String CANONICO = "ABCDEFGH JKMN".replace(" ", "");

    /**
     * Todas estas formas son la misma invitacion. Es el requisito real: el codigo se
     * dicta por telefono, se copia con los guiones y se teclea en el movil con el
     * corrector puesto.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "ABCDEFGHJKMN",
        "abcdefghjkmn",
        "ABCD-EFGH-JKMN",
        "  ABCD EFGH JKMN  ",
        "abcd-EFGH-jkMN",
    })
    @DisplayName("se normaliza igual venga como venga escrito")
    void normalizesEquivalentSpellings(String escrito) {
        assertThat(new InvitationCode(escrito).value()).isEqualTo(CANONICO);
    }

    @Test
    @DisplayName("las letras que se confunden al leer se corrigen solas")
    void fixesAmbiguousLetters() {
        // O es cero; I y L son uno. Quien lea "0" como "O" acierta igual.
        assertThat(new InvitationCode("O1234567890I").value()).isEqualTo("012345678901");
        assertThat(new InvitationCode("L1234567890O").value()).isEqualTo("112345678900");
    }

    @Test
    @DisplayName("se presenta en grupos de cuatro para poder leerlo")
    void formatsInGroupsOfFour() {
        assertThat(new InvitationCode(CANONICO).formatted()).isEqualTo("ABCD-EFGH-JKMN");
    }

    @Test
    @DisplayName("no delata el codigo al convertirlo en texto")
    void doesNotLeakInToString() {
        // Acaba en trazas y en registros: que no lleve el codigo dentro.
        assertThat(new InvitationCode(CANONICO).toString()).doesNotContain(CANONICO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ABCDEFGHJK",       // corto
        "ABCDEFGHJKMNPQ",   // largo
        "ABCDEFGHJKMU",     // la U no esta en el alfabeto
    })
    @DisplayName("un codigo mal formado se rechaza en el dominio")
    void rejectsMalformedCodes(String escrito) {
        assertThatThrownBy(() -> new InvitationCode(escrito))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("un codigo vacio se rechaza")
    void rejectsBlank() {
        assertThatThrownBy(() -> new InvitationCode("   "))
                .isInstanceOf(DomainException.class);
    }
}
