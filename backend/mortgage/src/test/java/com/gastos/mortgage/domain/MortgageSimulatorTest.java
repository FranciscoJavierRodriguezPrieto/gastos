package com.gastos.mortgage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.FinancingChoice;
import com.gastos.mortgage.domain.model.FinancingMode;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.model.ViabilityVerdict;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Motor de simulacion hipotecaria")
class MortgageSimulatorTest {

    private static final Offset<Double> ONE_EURO = Offset.offset(1.0);
    private static final HouseholdId HOUSEHOLD = HouseholdId.newId();

    private final MortgageSimulator simulator = MortgageSimulator.madridDefaults();

    private static AidProgram miPrimeraVivienda() {
        return AidProgram.create(HOUSEHOLD, "Mi Primera Vivienda", Percentage.of("95.00"),
                Money.euros(390_000), 35, true, true, "Verificar en BOCM");
    }

    private static AidProgram avalCien(boolean active) {
        return AidProgram.create(HOUSEHOLD, "Aval hasta el 100% para menores de 40",
                Percentage.of("100.00"), null, 40, true, active, "Sin verificar");
    }

    private static ApplicantProfile applicant(long netIncome, long otherDebts, int age) {
        return applicant(netIncome, otherDebts, age, true);
    }

    private static ApplicantProfile applicant(long netIncome, long otherDebts, int age,
                                              boolean firstHome) {
        return new ApplicantProfile(Money.euros(netIncome), Money.euros(otherDebts), age, firstHome);
    }

    private static SimulationRequest request(long price, long savings, long reserve, String rate,
                                             int years, ApplicantProfile applicant,
                                             FinancingChoice financing) {
        return new SimulationRequest(Money.euros(price), Money.euros(savings), Money.euros(reserve),
                Percentage.of(rate), years, applicant, financing);
    }

    @Nested
    @DisplayName("modo AUTOMATICO")
    class Automatic {

        @Test
        @DisplayName("sin programas dados de alta se aplica la financiacion estandar del 80%")
        void withoutProgramsFallsBackToStandard() {
            SimulationRequest req = request(200_000, 70_000, 6_000, "3.00", 30,
                    applicant(4_000, 225, 32), FinancingChoice.automatic());

            SimulationResult result = simulator.simulate(req, List.of());

            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("80.00"));
            assertThat(result.financingDecision().usesAidProgram()).isFalse();
            assertThat(result.financingDecision().notes())
                    .anyMatch(note -> note.contains("No hay programas de ayuda"));
        }

        @Test
        @DisplayName("aplica el programa que se cumple y lo deja por escrito")
        void appliesEligibleProgram() {
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(4_500, 225, 32), FinancingChoice.automatic());

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("95.00"));
            assertThat(result.financingDecision().appliedProgramName()).isEqualTo("Mi Primera Vivienda");
            assertThat(result.financingPlan().loanAmount()).isEqualTo(Money.euros(263_000));
            assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.VIABLE_AJUSTADA);
        }

        @Test
        @DisplayName("entre varios programas cumplidos gana el que mas financia")
        void picksTheBestEligibleProgram() {
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(4_500, 225, 32), FinancingChoice.automatic());

            SimulationResult result =
                    simulator.simulate(req, List.of(miPrimeraVivienda(), avalCien(true)));

            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("100.00"));
            assertThat(result.financingDecision().eligiblePrograms()).hasSize(2);
        }

        @Test
        @DisplayName("un programa desactivado no se aplica aunque se cumplan sus requisitos")
        void inactiveProgramIsIgnored() {
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(4_500, 225, 32), FinancingChoice.automatic());

            SimulationResult result =
                    simulator.simulate(req, List.of(miPrimeraVivienda(), avalCien(false)));

            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("95.00"));
            assertThat(result.financingDecision().evaluations())
                    .filteredOn(evaluation -> !evaluation.active())
                    .singleElement()
                    .satisfies(evaluation -> assertThat(evaluation.eligible()).isFalse());
        }

        @Test
        @DisplayName("superar la edad del programa devuelve la operacion al 80% estandar")
        void losingEligibilityDropsToStandard() {
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(4_500, 225, 42), FinancingChoice.automatic());

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("80.00"));
            assertThat(result.financingPlan().savingsSufficient()).isFalse();
            assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.INVIABLE);
            assertThat(result.financingDecision().evaluations())
                    .singleElement()
                    .satisfies(evaluation -> assertThat(evaluation.unmetCriteria())
                            .anyMatch(criterion -> criterion.contains("edad")));
        }
    }

    @Nested
    @DisplayName("modo PROGRAMA")
    class ChosenProgram {

        @Test
        @DisplayName("aplica el programa elegido cuando se cumple")
        void appliesChosenProgram() {
            AidProgram program = miPrimeraVivienda();
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(4_500, 225, 32), FinancingChoice.program(program.id()));

            SimulationResult result = simulator.simulate(req, List.of(program, avalCien(true)));

            // Aunque el aval del 100% financia mas, se respeta el que eligio el usuario.
            assertThat(result.financingDecision().appliedProgramId()).isEqualTo(program.id());
            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("95.00"));
        }

        @Test
        @DisplayName("si no se cumple, no se fuerza: se explica que falta y se aplica el estandar")
        void explainsWhyChosenProgramDoesNotApply() {
            AidProgram program = miPrimeraVivienda();
            SimulationRequest req = request(450_000, 200_000, 0, "3.00", 30,
                    applicant(6_000, 0, 32), FinancingChoice.program(program.id()));

            SimulationResult result = simulator.simulate(req, List.of(program));

            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("80.00"));
            assertThat(result.financingDecision().usesAidProgram()).isFalse();
            assertThat(result.financingDecision().notes())
                    .anyMatch(note -> note.contains("No se cumplen los requisitos"))
                    .anyMatch(note -> note.contains("precio"));
        }

        @Test
        @DisplayName("un programa borrado del catalogo no rompe la simulacion")
        void deletedProgramFallsBackToStandard() {
            SimulationRequest req = request(200_000, 70_000, 6_000, "3.00", 30,
                    applicant(4_000, 225, 32),
                    FinancingChoice.program(java.util.UUID.randomUUID()));

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("80.00"));
            assertThat(result.financingDecision().notes())
                    .anyMatch(note -> note.contains("ya no existe"));
        }
    }

    @Nested
    @DisplayName("modo MANUAL")
    class Manual {

        @Test
        @DisplayName("respeta el LTV escrito a mano sin comprobar ningun requisito")
        void manualIgnoresEveryRequirement() {
            // 52 anos y no es primera vivienda: no cumpliria ningun programa.
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(5_500, 225, 52, false),
                    FinancingChoice.manual(Percentage.of("100.00")));

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            assertThat(result.financingDecision().mode()).isEqualTo(FinancingMode.MANUAL);
            assertThat(result.financingDecision().appliedLoanToValue())
                    .isEqualTo(Percentage.of("100.00"));
            assertThat(result.financingDecision().usesAidProgram()).isFalse();
            assertThat(result.financingDecision().notes())
                    .anyMatch(note -> note.contains("No se ha comprobado ningun requisito"));
        }

        @Test
        @DisplayName("la advertencia por LTV alto dice que la cifra la puso el usuario")
        void manualWarningIsHonestAboutItsOrigin() {
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(5_500, 225, 32), FinancingChoice.manual(Percentage.of("100.00")));

            SimulationResult result = simulator.simulate(req, List.of());

            assertThat(result.viability().warnings())
                    .anyMatch(warning -> warning.contains("fijado a mano"));
        }
    }

    @Nested
    @DisplayName("el calculo financiero no depende del modo")
    class ModeIndependentMath {

        @Test
        @DisplayName("mismo LTV por tres caminos distintos produce identica cuota y DTI")
        void sameLoanToValueSameNumbers() {
            AidProgram program = miPrimeraVivienda();
            ApplicantProfile profile = applicant(4_500, 225, 32);

            SimulationResult viaProgram = simulator.simulate(
                    request(280_000, 50_000, 5_000, "3.00", 30, profile, FinancingChoice.automatic()),
                    List.of(program));
            SimulationResult viaChosen = simulator.simulate(
                    request(280_000, 50_000, 5_000, "3.00", 30, profile,
                            FinancingChoice.program(program.id())),
                    List.of(program));
            SimulationResult viaManual = simulator.simulate(
                    request(280_000, 50_000, 5_000, "3.00", 30, profile,
                            FinancingChoice.manual(Percentage.of("95.00"))),
                    List.of());

            assertThat(viaProgram.monthlyPayment()).isEqualTo(viaChosen.monthlyPayment())
                    .isEqualTo(viaManual.monthlyPayment());
            assertThat(viaProgram.housingDti()).isEqualTo(viaChosen.housingDti())
                    .isEqualTo(viaManual.housingDti());
            assertThat(viaProgram.totalDti()).isEqualTo(viaManual.totalDti());
        }
    }

    @Nested
    @DisplayName("escenarios de viabilidad")
    class Viability {

        @Test
        @DisplayName("INVIABLE: el ahorro no cubre el 10% de gastos no financiables")
        void inviableBecauseSavingsDoNotCoverUpfrontCosts() {
            SimulationRequest req = request(280_000, 10_000, 0, "1.78", 20,
                    applicant(3_400, 225, 32), FinancingChoice.automatic());

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            assertThat(result.upfrontCosts().total()).isEqualTo(Money.euros(28_000));
            assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.INVIABLE);
            assertThat(result.viability().blockingReasons())
                    .anyMatch(reason -> reason.startsWith("Ahorro insuficiente"));
        }

        @Test
        @DisplayName("INVIABLE: la cuota supera el 30% de DTI vivienda")
        void inviableBecauseHousingDtiExceedsLimit() {
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(2_500, 225, 32), FinancingChoice.automatic());

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            assertThat(result.housingDti().value().doubleValue()).isGreaterThan(30.0);
            assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.INVIABLE);
            assertThat(result.viability().blockingReasons())
                    .anyMatch(reason -> reason.contains("DTI vivienda"));
        }

        @Test
        @DisplayName("VIABLE PERO AJUSTADA: cumple limites pero depende del aval al 94% de LTV")
        void viableButTightBecauseOfHighLoanToValue() {
            SimulationRequest req = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(4_500, 225, 32), FinancingChoice.automatic());

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            assertThat(result.financingPlan().loanAmount()).isEqualTo(Money.euros(263_000));
            assertThat(result.monthlyPayment().amount().doubleValue())
                    .isCloseTo(1_108.82, ONE_EURO);
            assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.VIABLE_AJUSTADA);
            assertThat(result.viability().blockingReasons()).isEmpty();
            assertThat(result.viability().warnings())
                    .anyMatch(warning -> warning.contains("Mi Primera Vivienda"));
        }

        @Test
        @DisplayName("OPTIMA: cuota holgada, LTV del 77% y colchon tras la firma")
        void optimalScenario() {
            SimulationRequest req = request(200_000, 70_000, 6_000, "3.00", 30,
                    applicant(4_000, 225, 32), FinancingChoice.automatic());

            SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

            // 200.000 EUR de vivienda habitual: ITP al 5,4% (10.800) + 4% de gastos
            // (8.000) = 18.800. De los 64.000 disponibles tras el colchon, 45.200 van a la
            // entrada y el prestamo queda en 154.800.
            assertThat(result.upfrontCosts().total()).isEqualTo(Money.euros(18_800));
            assertThat(result.financingPlan().loanAmount()).isEqualTo(Money.euros(154_800));
            assertThat(result.monthlyPayment().amount().doubleValue()).isCloseTo(652.65, ONE_EURO);
            assertThat(result.housingDti().value().doubleValue()).isCloseTo(16.32, Offset.offset(0.05));
            assertThat(result.totalDti().value().doubleValue()).isCloseTo(21.94, Offset.offset(0.05));
            assertThat(result.viability().verdict()).isEqualTo(ViabilityVerdict.OPTIMA);
            assertThat(result.viability().warnings()).isEmpty();
        }

        @Test
        @DisplayName("la deuda previa del coche solo mueve el DTI total, no el de vivienda")
        void carLoanOnlyAffectsTotalDti() {
            SimulationRequest withoutCar = request(200_000, 70_000, 6_000, "3.00", 30,
                    applicant(4_000, 0, 32), FinancingChoice.automatic());
            SimulationRequest withCar = request(200_000, 70_000, 6_000, "3.00", 30,
                    applicant(4_000, 225, 32), FinancingChoice.automatic());

            SimulationResult a = simulator.simulate(withoutCar, List.of());
            SimulationResult b = simulator.simulate(withCar, List.of());

            assertThat(a.housingDti()).isEqualTo(b.housingDti());
            assertThat(b.totalDti().value().doubleValue())
                    .isGreaterThan(a.totalDti().value().doubleValue());
            assertThat(b.viability().debtToIncome().residualIncome())
                    .isEqualTo(a.viability().debtToIncome().residualIncome().minus(Money.euros(225)));
        }
    }

    @Nested
    @DisplayName("barridos de sensibilidad")
    class Sweeps {

        @Test
        @DisplayName("el barrido de ingresos reduce el DTI al subir la nomina del hogar")
        void incomeSweepImprovesRatios() {
            SimulationRequest base = request(280_000, 50_000, 5_000, "3.00", 30,
                    applicant(3_400, 225, 32), FinancingChoice.automatic());

            List<SimulationResult> sweep = simulator.sweepByIncome(base,
                    List.of(Money.euros(3_400), Money.euros(3_700), Money.euros(4_000)),
                    List.of(miPrimeraVivienda()));

            assertThat(sweep).hasSize(3);
            assertThat(sweep.get(0).housingDti().value().doubleValue())
                    .isGreaterThan(sweep.get(2).housingDti().value().doubleValue());
            assertThat(sweep).allSatisfy(result ->
                    assertThat(result.monthlyPayment()).isEqualTo(sweep.get(0).monthlyPayment()));
        }

        @Test
        @DisplayName("el barrido de tipos encarece la cuota al subir el TIN")
        void rateSweepIncreasesPayment() {
            SimulationRequest base = request(200_000, 70_000, 6_000, "2.00", 30,
                    applicant(4_000, 225, 32), FinancingChoice.automatic());

            List<SimulationResult> sweep = simulator.sweepByRate(base,
                    List.of(Percentage.of("2.00"), Percentage.of("3.00"), Percentage.of("4.00")),
                    List.of(miPrimeraVivienda()));

            assertThat(sweep.get(0).monthlyPayment()).isLessThan(sweep.get(1).monthlyPayment());
            assertThat(sweep.get(1).monthlyPayment()).isLessThan(sweep.get(2).monthlyPayment());
        }
    }

    @Test
    @DisplayName("el coste total de la operacion suma precio, intereses y gastos")
    void totalCostAggregatesEveryComponent() {
        SimulationRequest req = request(200_000, 70_000, 6_000, "3.00", 30,
                applicant(4_000, 225, 32), FinancingChoice.automatic());

        SimulationResult result = simulator.simulate(req, List.of(miPrimeraVivienda()));

        Money expected = Money.euros(200_000)
                .plus(result.totalInterest())
                .plus(result.upfrontCosts().total());
        assertThat(result.totalCostOfOwnership()).isEqualTo(expected);
        assertThat(result.cashRequiredAtSigning()).isEqualTo(Money.euros(64_000));
    }
}
