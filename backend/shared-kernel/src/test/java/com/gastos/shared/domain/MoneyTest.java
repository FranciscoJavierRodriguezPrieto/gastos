package com.gastos.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Money")
class MoneyTest {

    @Nested
    @DisplayName("construccion")
    class Construction {

        @Test
        @DisplayName("normaliza siempre a dos decimales con redondeo HALF_UP")
        void roundsToTwoDecimals() {
            assertThat(Money.euros("10.005").amount()).isEqualByComparingTo("10.01");
            assertThat(Money.euros("10.004").amount()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("rechaza importes nulos")
        void rejectsNull() {
            assertThatThrownBy(() -> Money.euros((BigDecimal) null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("amount");
        }
    }

    @Nested
    @DisplayName("aritmetica")
    class Arithmetic {

        @Test
        @DisplayName("suma y resta sin error de coma flotante")
        void addsWithoutFloatingPointError() {
            Money result = Money.euros("0.10").plus(Money.euros("0.20"));

            assertThat(result.amount()).isEqualByComparingTo("0.30");
            assertThat(result).isEqualTo(Money.euros("0.30"));
        }

        @Test
        @DisplayName("aplica un porcentaje sobre el importe")
        void appliesPercentage() {
            Money itp = Money.euros(280_000).percentageOf(Percentage.of("6.00"));

            assertThat(itp.amount()).isEqualByComparingTo("16800.00");
        }

        @Test
        @DisplayName("calcula el ratio entre dos importes con precision extendida")
        void computesRatio() {
            BigDecimal ratio = Money.euros("1257.63").ratioTo(Money.euros("3400.00"));

            assertThat(ratio.doubleValue()).isCloseTo(0.36989, org.assertj.core.data.Offset.offset(0.00001));
        }

        @Test
        @DisplayName("impide operar entre divisas distintas")
        void rejectsCurrencyMismatch() {
            Money euros = Money.euros(100);
            Money dollars = Money.of(new BigDecimal("100"), Currency.getInstance("USD"));

            assertThatThrownBy(() -> euros.plus(dollars))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("divisas distintas");
        }

        @Test
        @DisplayName("impide dividir por cero")
        void rejectsDivisionByZero() {
            assertThatThrownBy(() -> Money.euros(100).dividedBy(BigDecimal.ZERO))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("impide calcular un ratio sobre cero")
        void rejectsRatioOverZero() {
            assertThatThrownBy(() -> Money.euros(100).ratioTo(Money.zero()))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("comparacion")
    class Comparison {

        @ParameterizedTest(name = "{0} frente a {1} -> mayor: {2}")
        @CsvSource({
                "100.00, 99.99, true",
                "100.00, 100.00, false",
                "99.99, 100.00, false"
        })
        void comparesAmounts(String left, String right, boolean expectedGreater) {
            assertThat(Money.euros(left).isGreaterThan(Money.euros(right))).isEqualTo(expectedGreater);
        }

        @Test
        @DisplayName("considera iguales importes con distinta escala de origen")
        void equalityIgnoresScale() {
            assertThat(Money.euros("10")).isEqualTo(Money.euros("10.00"));
            assertThat(Money.euros("10")).hasSameHashCodeAs(Money.euros("10.00"));
        }
    }
}
