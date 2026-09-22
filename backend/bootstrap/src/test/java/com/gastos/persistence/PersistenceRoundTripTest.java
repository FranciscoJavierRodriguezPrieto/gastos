package com.gastos.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountType;
import com.gastos.accounts.domain.model.Ownership;
import com.gastos.accounts.domain.port.AccountRepository;
import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.expenses.domain.port.FixedExpenseRepository;
import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.mortgage.domain.model.FinancingChoice;
import com.gastos.mortgage.domain.model.FinancingMode;
import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.port.AidProgramRepository;
import com.gastos.mortgage.domain.port.MortgageScenarioRepository;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import com.gastos.shared.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comprueba que lo que entra en la base de datos sale igual.
 *
 * <p>Un mapper de persistencia falla en silencio: si pierde un decimal, confunde dos
 * enumerados o descarta un campo opcional, nada casca — simplemente los datos quedan
 * mal. Por eso cada agregado se guarda, se relee y se compara campo a campo.</p>
 *
 * <p>Se prueba contra la base de datos de verdad, con las mismas migraciones de Flyway
 * que se despliegan, no con dobles de los repositorios.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Ida y vuelta contra la base de datos")
class PersistenceRoundTripTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private FixedExpenseRepository fixedExpenseRepository;

    @Autowired
    private AidProgramRepository aidProgramRepository;

    @Autowired
    private MortgageScenarioRepository scenarioRepository;

    @Test
    @DisplayName("una cuenta conjunta conserva saldo, titulares e instante de actualizacion")
    void accountSurvivesRoundTrip() {
        HouseholdId household = HouseholdId.newId();
        UserId first = UserId.newId();
        UserId second = UserId.newId();
        Instant updatedAt = Instant.parse("2026-03-01T10:15:30Z");

        Account saved = accountRepository.save(Account.open(household, "Cuenta conjunta",
                "Banco Ejemplo", AccountType.CORRIENTE, Ownership.CONJUNTA, Set.of(first, second),
                Money.euros("2210.55"), updatedAt));

        Account reloaded = accountRepository.findById(household, saved.id()).orElseThrow();

        assertThat(reloaded.alias()).isEqualTo("Cuenta conjunta");
        assertThat(reloaded.bankName()).isEqualTo("Banco Ejemplo");
        assertThat(reloaded.type()).isEqualTo(AccountType.CORRIENTE);
        assertThat(reloaded.ownership()).isEqualTo(Ownership.CONJUNTA);
        assertThat(reloaded.balance()).isEqualTo(Money.euros("2210.55"));
        assertThat(reloaded.holders()).containsExactlyInAnyOrder(first, second);
        assertThat(reloaded.balanceUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("un gasto conserva importe, categoria y periodicidad")
    void expenseSurvivesRoundTrip() {
        HouseholdId household = HouseholdId.newId();
        UserId user = UserId.newId();

        Expense saved = expenseRepository.save(Expense.register(household, user, "Seguro hogar",
                Money.euros("300.00"), ExpenseCategory.SEGUROS, Recurrence.ANUAL,
                LocalDate.of(2026, 3, 10), null));

        Expense reloaded = expenseRepository.findById(household, saved.id()).orElseThrow();

        assertThat(reloaded.description()).isEqualTo("Seguro hogar");
        assertThat(reloaded.amount()).isEqualTo(Money.euros("300.00"));
        assertThat(reloaded.category()).isEqualTo(ExpenseCategory.SEGUROS);
        assertThat(reloaded.recurrence()).isEqualTo(Recurrence.ANUAL);
        assertThat(reloaded.incurredOn()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(reloaded.accountId()).isNull();
        assertThat(reloaded.isStableCommitment()).isTrue();
    }

    @Test
    @DisplayName("la consulta por mes usa el rango de fechas y excluye los meses vecinos")
    void expensesAreFilteredByMonth() {
        HouseholdId household = HouseholdId.newId();
        UserId user = UserId.newId();

        expenseRepository.save(Expense.register(household, user, "Ultimo dia de febrero",
                Money.euros("10.00"), ExpenseCategory.OCIO, Recurrence.PUNTUAL,
                LocalDate.of(2026, 2, 28), null));
        expenseRepository.save(Expense.register(household, user, "Primer dia de marzo",
                Money.euros("20.00"), ExpenseCategory.OCIO, Recurrence.PUNTUAL,
                LocalDate.of(2026, 3, 1), null));
        expenseRepository.save(Expense.register(household, user, "Ultimo dia de marzo",
                Money.euros("30.00"), ExpenseCategory.OCIO, Recurrence.PUNTUAL,
                LocalDate.of(2026, 3, 31), null));
        expenseRepository.save(Expense.register(household, user, "Primer dia de abril",
                Money.euros("40.00"), ExpenseCategory.OCIO, Recurrence.PUNTUAL,
                LocalDate.of(2026, 4, 1), null));

        assertThat(expenseRepository.findByMonth(household, YearMonth.of(2026, 3)))
                .extracting(Expense::description)
                .containsExactlyInAnyOrder("Primer dia de marzo", "Ultimo dia de marzo");
    }

    @Test
    @DisplayName("un programa sin limites conserva los nulos, que no son ceros")
    void aidProgramKeepsNullLimits() {
        HouseholdId household = HouseholdId.newId();

        AidProgram saved = aidProgramRepository.save(AidProgram.create(household, "Sin topes",
                Percentage.of("90.00"), null, null, false, true, "Nota de origen"));

        AidProgram reloaded = aidProgramRepository.findById(household, saved.id()).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("Sin topes");
        assertThat(reloaded.maxLoanToValue()).isEqualTo(Percentage.of("90.00"));
        assertThat(reloaded.maxPropertyPrice()).isNull();
        assertThat(reloaded.maxApplicantAge()).isNull();
        assertThat(reloaded.requiresFirstHome()).isFalse();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.sourceNote()).isEqualTo("Nota de origen");
    }

    @Test
    @DisplayName("un programa con limites conserva precio y edad maximos")
    void aidProgramKeepsLimits() {
        HouseholdId household = HouseholdId.newId();

        AidProgram saved = aidProgramRepository.save(AidProgram.create(household, "Con topes",
                Percentage.of("95.00"), Money.euros(390_000), 35, true, false, ""));

        AidProgram reloaded = aidProgramRepository.findById(household, saved.id()).orElseThrow();

        assertThat(reloaded.maxPropertyPrice()).isEqualTo(Money.euros(390_000));
        assertThat(reloaded.maxApplicantAge()).isEqualTo(35);
        assertThat(reloaded.requiresFirstHome()).isTrue();
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @DisplayName("un escenario en modo MANUAL conserva el LTV fijado a mano")
    void manualScenarioSurvivesRoundTrip() {
        HouseholdId household = HouseholdId.newId();
        SimulationRequest request = new SimulationRequest(
                Money.euros(280_000), Money.euros(50_000), Money.euros(5_000),
                Percentage.of("3.1750"), 30,
                new ApplicantProfile(Money.euros("4500.75"), Money.euros("225.50"), 32, true),
                FinancingChoice.manual(Percentage.of("100.00")));

        MortgageScenario saved = scenarioRepository.save(MortgageScenario.create(household,
                "Piso de 280k", request, Instant.parse("2026-03-01T09:00:00Z")));

        MortgageScenario reloaded =
                scenarioRepository.findById(household, saved.id()).orElseThrow();
        SimulationRequest back = reloaded.request();

        assertThat(reloaded.name()).isEqualTo("Piso de 280k");
        assertThat(back.propertyPrice()).isEqualTo(Money.euros(280_000));
        assertThat(back.targetReserve()).isEqualTo(Money.euros(5_000));
        // Cuatro decimales en el tipo: si la escala se perdiera, aqui se veria.
        assertThat(back.annualNominalRate()).isEqualTo(Percentage.of("3.1750"));
        assertThat(back.termYears()).isEqualTo(30);
        assertThat(back.applicant().netMonthlyIncome()).isEqualTo(Money.euros("4500.75"));
        assertThat(back.applicant().otherMonthlyDebts()).isEqualTo(Money.euros("225.50"));
        assertThat(back.financing().mode()).isEqualTo(FinancingMode.MANUAL);
        assertThat(back.financing().manualLoanToValue()).isEqualTo(Percentage.of("100.00"));
        assertThat(back.financing().programId()).isNull();
    }

    @Test
    @DisplayName("un escenario en modo PROGRAMA conserva el programa elegido")
    void programScenarioSurvivesRoundTrip() {
        HouseholdId household = HouseholdId.newId();
        AidProgram program = aidProgramRepository.save(AidProgram.create(household, "Mi Primera",
                Percentage.of("95.00"), Money.euros(390_000), 35, true, true, ""));

        SimulationRequest request = new SimulationRequest(
                Money.euros(200_000), Money.euros(70_000), Money.euros(6_000),
                Percentage.of("3.00"), 30,
                new ApplicantProfile(Money.euros(4_000), Money.euros(225), 32, true),
                FinancingChoice.program(program.id()));

        MortgageScenario saved = scenarioRepository.save(MortgageScenario.create(household,
                "Con programa", request, Instant.parse("2026-03-01T09:00:00Z")));

        MortgageScenario reloaded =
                scenarioRepository.findById(household, saved.id()).orElseThrow();

        assertThat(reloaded.request().financing().mode()).isEqualTo(FinancingMode.PROGRAMA);
        assertThat(reloaded.request().financing().programId()).isEqualTo(program.id());
    }

    @Test
    @DisplayName("ningun repositorio devuelve datos de otro hogar")
    void repositoriesNeverCrossHouseholds() {
        HouseholdId mine = HouseholdId.newId();
        HouseholdId theirs = HouseholdId.newId();
        UserId user = UserId.newId();

        Account account = accountRepository.save(Account.open(mine, "Mi cuenta", "Banco",
                AccountType.AHORRO, Ownership.INDIVIDUAL, Set.of(user), Money.euros(100),
                Instant.now()));
        Expense expense = expenseRepository.save(Expense.register(mine, user, "Mi gasto",
                Money.euros(10), ExpenseCategory.OCIO, Recurrence.PUNTUAL,
                LocalDate.of(2026, 3, 1), null));
        AidProgram program = aidProgramRepository.save(AidProgram.create(mine, "Mi programa",
                Percentage.of("95.00"), null, null, false, true, ""));

        assertThat(accountRepository.findById(theirs, account.id())).isEmpty();
        assertThat(expenseRepository.findById(theirs, expense.id())).isEmpty();
        assertThat(aidProgramRepository.findById(theirs, program.id())).isEmpty();
        assertThat(accountRepository.findAllByHousehold(theirs)).isEmpty();
    }

    /**
     * La reserva de un mes la arbitra la clave primaria de {@code fixed_expense_application},
     * no una comprobacion previa en memoria.
     *
     * <p>Este test existe porque el fallo real se escapo a los demas: la pantalla pide el
     * listado y el resumen <strong>en paralelo</strong>, los dos expandian el mes, y el
     * segundo reventaba con una violacion de clave. Las pruebas que llamaban en serie no
     * podian verlo; esta va directa al arbitro.</p>
     */
    @Test
    @DisplayName("un mes solo se puede reservar una vez, y el segundo no revienta")
    void claimingAMonthIsExclusive() {
        HouseholdId household = HouseholdId.newId();
        FixedExpense alquiler = fixedExpenseRepository.save(FixedExpense.create(
                household, UserId.newId(), "Alquiler", Money.euros("800.00"),
                ExpenseCategory.VIVIENDA, 1, null, YearMonth.of(2026, 3)));

        assertThat(fixedExpenseRepository.claimFor(alquiler.id(), YearMonth.of(2026, 3))).isTrue();
        // El segundo NO lanza: devuelve false y quien llama simplemente no genera nada.
        assertThat(fixedExpenseRepository.claimFor(alquiler.id(), YearMonth.of(2026, 3))).isFalse();
        // Otro mes sigue libre.
        assertThat(fixedExpenseRepository.claimFor(alquiler.id(), YearMonth.of(2026, 4))).isTrue();

        assertThat(fixedExpenseRepository.findAppliedIn(household, YearMonth.of(2026, 3)))
                .containsExactly(alquiler.id());
    }
}
