package com.gastos.mortgage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.FinancingChoice;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.model.UpfrontCosts;
import com.gastos.mortgage.domain.policy.PurchaseCostsPolicy;
import com.gastos.mortgage.domain.policy.ReferenceAidPrograms;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Normativa de vivienda de la Comunidad de Madrid, vigente desde agosto de 2026.
 *
 * <p>Cada caso sale de la norma, no del codigo: si un test falla, lo primero es volver a
 * leer la norma, no ajustar el numero esperado. Los bordes (40, 45, 50 anos; 250.000 y
 * 425.000 EUR) estan todos, porque es exactamente ahi donde se cuela un "mayor que" que
 * deberia ser "mayor o igual".</p>
 */
@DisplayName("Normativa de Madrid 2026")
class NormativaMadrid2026Test {

    private static final PurchaseCostsPolicy MADRID = PurchaseCostsPolicy.madridSecondHand();

    private static ApplicantProfile perfil(int edad, boolean familia, boolean numerosa,
                                           boolean habitual) {
        return new ApplicantProfile(Money.euros(4_000), Money.zero(), edad, true, familia, numerosa,
                habitual);
    }

    private static ApplicantProfile normal(int edad) {
        return perfil(edad, false, false, true);
    }

    /**
     * ITP de la Comunidad de Madrid para vivienda usada.
     *
     * <p>Fuente: comunidad.madrid, Transmisiones Patrimoniales Onerosas y Beneficios
     * Fiscales (consultado el 21/09/2026).</p>
     */
    @Nested
    @DisplayName("ITP")
    class Itp {

        @ParameterizedTest(name = "{0} EUR, habitual={1}, numerosa={2} -> {3}%")
        @CsvSource({
                // Bonificacion del 10% de la cuota hasta 250.000, sin limite de edad.
                "200000, true,  false, 5.40",
                "250000, true,  false, 5.40",
                // Un euro por encima del limite ya paga el tipo general.
                "250001, true,  false, 6.00",
                "400000, true,  false, 6.00",
                // Sin residencia habitual no hay reduccion ninguna.
                "200000, false, false, 6.00",
                // Familia numerosa: 4%, y no depende del precio.
                "200000, true,  true,  4.00",
                "400000, true,  true,  4.00",
                // ...pero tambien exige vivienda habitual.
                "200000, false, true,  6.00",
        })
        void appliesTheRightRate(long precio, boolean habitual, boolean numerosa, String tipo) {
            UpfrontCosts costes = UpfrontCosts.of(Money.euros(precio), MADRID,
                    perfil(55, numerosa, numerosa, habitual));

            assertThat(costes.transferTaxRate()).isEqualTo(Percentage.of(tipo));
        }

        /** La bonificacion es de la vivienda, no del comprador: a los 60 tambien. */
        @Test
        @DisplayName("la bonificacion del 10% no depende de la edad")
        void rebateIsNotAgeBound() {
            UpfrontCosts joven = UpfrontCosts.of(Money.euros(240_000), MADRID, normal(30));
            UpfrontCosts mayor = UpfrontCosts.of(Money.euros(240_000), MADRID, normal(60));

            assertThat(mayor.transferTax()).isEqualTo(joven.transferTax());
            assertThat(mayor.transferTax()).isEqualTo(Money.euros(12_960));
        }

        @Test
        @DisplayName("el motivo del tipo aplicado viaja con la cifra")
        void explainsTheRate() {
            assertThat(UpfrontCosts.of(Money.euros(300_000), MADRID, normal(30)).transferTaxBasis())
                    .contains("250000");
            assertThat(UpfrontCosts.of(Money.euros(300_000), MADRID, perfil(30, true, true, true))
                    .transferTaxBasis()).contains("familia numerosa");
        }
    }

    /**
     * Mi Primera Vivienda: Orden de 27 de julio de 2026, BOCM num. 186, articulos 2 a 4.
     *
     * <p>Se prueba contra el catalogo que se instala de verdad y a traves del simulador, en
     * modo automatico: lo que importa no es que cada programa este bien escrito, sino que
     * el hogar acabe en el tramo que le toca.</p>
     */
    @Nested
    @DisplayName("Mi Primera Vivienda")
    class MiPrimeraVivienda {

        private final MortgageSimulator simulador = MortgageSimulator.madridDefaults();
        private final List<AidProgram> catalogo = ReferenceAidPrograms.installFor(HouseholdId.newId());

        private SimulationResult simular(long precio, ApplicantProfile solicitante) {
            // Ahorro justo para que el LTV lo fije el programa y no lo que aporta el hogar.
            SimulationRequest peticion = new SimulationRequest(Money.euros(precio), Money.euros(1_000),
                    Money.zero(), Percentage.of("3.00"), 30, solicitante, FinancingChoice.automatic());
            return simulador.simulate(peticion, catalogo);
        }

        @ParameterizedTest(name = "a los {0} anos, hasta el {1}%")
        @CsvSource({
                "25, 100.00",
                // "No superen los cuarenta anos": los 40 aun cuentan.
                "40, 100.00",
                "41,  95.00",
                "45,  95.00",
                "46,  90.00",
                "50,  90.00",
                // A partir de 51 ya no hay programa: financiacion estandar.
                "51,  80.00",
        })
        void appliesTheAgeBracket(int edad, String ltv) {
            assertThat(simular(300_000, normal(edad)).financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of(ltv));
        }

        @Test
        @DisplayName("una familia con hijos llega al 100% sin limite de edad")
        void familiesHaveNoAgeLimit() {
            assertThat(simular(300_000, perfil(58, true, false, true))
                    .financingDecision().appliedLoanToValue()).isEqualTo(Percentage.of("100.00"));
        }

        @Test
        @DisplayName("la familia numerosa entra por la misma via aunque no marque hijos menores")
        void largeFamilyAlsoQualifies() {
            assertThat(simular(300_000, perfil(58, false, true, true))
                    .financingDecision().appliedLoanToValue()).isEqualTo(Percentage.of("100.00"));
        }

        @ParameterizedTest(name = "precio {0} -> {1}%")
        @CsvSource({
                "425000, 100.00",
                // Un euro por encima de los 425.000 del articulo 4.b queda fuera.
                "425001,  80.00",
        })
        void respectsTheMaximumPrice(long precio, String ltv) {
            assertThat(simular(precio, normal(30)).financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of(ltv));
        }

        @Test
        @DisplayName("sin residencia habitual no hay programa de primera vivienda")
        void requiresPrimaryResidence() {
            assertThat(simular(300_000, perfil(30, false, false, false))
                    .financingDecision().appliedLoanToValue()).isEqualTo(Percentage.of("80.00"));
        }

        @Test
        @DisplayName("el catalogo cita la norma de la que sale")
        void catalogCitesItsSource() {
            assertThat(catalogo).hasSize(4)
                    .allSatisfy(p -> assertThat(p.sourceNote()).contains("BOCM 186"))
                    .allSatisfy(p -> assertThat(p.isActive()).isTrue());
        }
    }
}
