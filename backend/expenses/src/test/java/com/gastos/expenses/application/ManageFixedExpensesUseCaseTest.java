package com.gastos.expenses.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.model.FixedExpenseId;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.expenses.domain.port.FixedExpenseRepository;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que hace util a los gastos fijos: que aparezcan solos cada mes, que no se dupliquen,
 * y que tocar un mes no arrastre a los demas.
 */
@DisplayName("Gastos fijos")
class ManageFixedExpensesUseCaseTest {

    private static final YearMonth MARZO = YearMonth.of(2026, 3);
    private static final Instant EN_MARZO = Instant.parse("2026-03-10T09:00:00Z");

    private final FijosEnMemoria fijos = new FijosEnMemoria();
    private final GastosEnMemoria gastos = new GastosEnMemoria();
    private final Clock reloj = Clock.fixed(EN_MARZO, ZoneOffset.UTC);

    private final HouseholdId hogar = HouseholdId.newId();
    private final UserId usuario = UserId.newId();

    private ManageFixedExpensesUseCase caso;

    @BeforeEach
    void setUp() {
        caso = new ManageFixedExpensesUseCase(fijos, gastos, reloj);
    }

    private FixedExpense darDeAlta(String descripcion, int importe) {
        return caso.create(hogar, usuario, new FixedExpenseCommand(descripcion,
                Money.euros(importe), ExpenseCategory.VIVIENDA, 1, null, null));
    }

    @Test
    @DisplayName("sin mes de inicio, empieza en el mes en curso")
    void defaultsToCurrentMonth() {
        assertThat(darDeAlta("Alquiler", 800).startMonth()).isEqualTo(MARZO);
    }

    @Test
    @DisplayName("no se puede dar de alta empezando en un mes ya pasado")
    void cannotStartInThePast() {
        assertThatThrownBy(() -> caso.create(hogar, usuario, new FixedExpenseCommand("Alquiler",
                Money.euros(800), ExpenseCategory.VIVIENDA, 1, null, MARZO.minusMonths(1))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("mes ya pasado");
    }

    @Test
    @DisplayName("abrir el mes genera el gasto")
    void expandingCreatesTheExpense() {
        darDeAlta("Alquiler", 800);

        assertThat(caso.expand(hogar, MARZO)).isEqualTo(1);
        assertThat(gastos.delMes(MARZO)).singleElement()
                .satisfies(g -> {
                    assertThat(g.description()).isEqualTo("Alquiler");
                    assertThat(g.amount()).isEqualTo(Money.euros(800));
                    assertThat(g.comesFromFixedExpense()).isTrue();
                });
    }

    /**
     * La reserva la arbitra la clave primaria, no una comprobacion previa: es lo que
     * salva la carrera entre el listado y el resumen, que la pantalla pide a la vez.
     */
    @Test
    @DisplayName("reservar un mes dos veces solo lo concede una")
    void claimingIsExclusive() {
        FixedExpense alquiler = darDeAlta("Alquiler", 800);

        assertThat(fijos.claimFor(alquiler.id(), MARZO)).isTrue();
        assertThat(fijos.claimFor(alquiler.id(), MARZO)).isFalse();
        // Y quien perdio la carrera no genera nada.
        assertThat(caso.expand(hogar, MARZO)).isZero();
        assertThat(gastos.delMes(MARZO)).isEmpty();
    }

    /** Recargar la pantalla no puede duplicar el alquiler. */
    @Test
    @DisplayName("expandir dos veces el mismo mes no duplica nada")
    void expandingIsIdempotent() {
        darDeAlta("Alquiler", 800);

        caso.expand(hogar, MARZO);
        assertThat(caso.expand(hogar, MARZO)).isZero();
        assertThat(caso.expand(hogar, MARZO)).isZero();

        assertThat(gastos.delMes(MARZO)).hasSize(1);
    }

    /**
     * El caso que motivo la funcionalidad: la luz son 50 EUR, pero en marzo fueron 95.
     * Se corrige marzo y abril tiene que seguir saliendo a 50.
     */
    @Test
    @DisplayName("corregir el gasto de un mes no afecta a los demas")
    void fixingOneMonthLeavesTheRestAlone() {
        caso.create(hogar, usuario, new FixedExpenseCommand("Luz", Money.euros(50),
                ExpenseCategory.SUMINISTROS, 10, null, null));

        caso.expand(hogar, MARZO);
        Expense marzo = gastos.delMes(MARZO).get(0);
        marzo.correct("Luz", Money.euros(95), marzo.incurredOn());
        gastos.save(marzo);

        caso.expand(hogar, MARZO.plusMonths(1));

        assertThat(gastos.delMes(MARZO).get(0).amount()).isEqualTo(Money.euros(95));
        assertThat(gastos.delMes(MARZO.plusMonths(1)).get(0).amount()).isEqualTo(Money.euros(50));
    }

    /** Historico sin versionar importes: cada mes ya guarda el suyo. */
    @Test
    @DisplayName("subir el importe solo afecta a los meses aun no generados")
    void raisingTheAmountOnlyAffectsFutureMonths() {
        FixedExpense alquiler = darDeAlta("Alquiler", 800);
        caso.expand(hogar, MARZO);

        caso.update(hogar, alquiler.id(), new FixedExpenseCommand("Alquiler", Money.euros(850),
                ExpenseCategory.VIVIENDA, 1, null, null));
        caso.expand(hogar, MARZO.plusMonths(1));

        assertThat(gastos.delMes(MARZO).get(0).amount()).isEqualTo(Money.euros(800));
        assertThat(gastos.delMes(MARZO.plusMonths(1)).get(0).amount()).isEqualTo(Money.euros(850));
    }

    /**
     * Sin la marca de aplicado, borrar seria un deseo que se deshace al recargar: la
     * siguiente lectura del mes lo volveria a generar.
     */
    @Test
    @DisplayName("si borras el gasto generado de un mes, no vuelve")
    void deletingTheGeneratedExpenseSticks() {
        darDeAlta("Alquiler", 800);
        caso.expand(hogar, MARZO);

        gastos.delete(hogar, gastos.delMes(MARZO).get(0).id());
        caso.expand(hogar, MARZO);

        assertThat(gastos.delMes(MARZO)).isEmpty();
    }

    @Test
    @DisplayName("dado de baja, deja de generar desde el mes que viene")
    void discontinuingStopsNextMonth() {
        FixedExpense alquiler = darDeAlta("Alquiler", 800);

        caso.discontinue(hogar, alquiler.id());

        // Marzo, el mes en curso, sigue generandose: normalmente ya se ha pagado.
        assertThat(caso.expand(hogar, MARZO)).isEqualTo(1);
        assertThat(caso.expand(hogar, MARZO.plusMonths(1))).isZero();
    }

    @Test
    @DisplayName("borrar la plantilla conserva los gastos que ya genero")
    void deletingTheTemplateKeepsHistory() {
        FixedExpense alquiler = darDeAlta("Alquiler", 800);
        caso.expand(hogar, MARZO);

        caso.delete(hogar, alquiler.id());

        assertThat(fijos.findAll(hogar)).isEmpty();
        assertThat(gastos.delMes(MARZO)).hasSize(1);
    }

    @Test
    @DisplayName("un gasto fijo de otro hogar no se ve ni se toca")
    void isolatedByHousehold() {
        FixedExpense ajeno = darDeAlta("Alquiler", 800);
        HouseholdId otro = HouseholdId.newId();

        assertThat(caso.list(otro)).isEmpty();
        assertThatThrownBy(() -> caso.findById(otro, ajeno.id()))
                .isInstanceOf(com.gastos.shared.domain.ResourceNotFoundException.class);
        assertThat(caso.expand(otro, MARZO)).isZero();
    }

    // --- Dobles en memoria -------------------------------------------------------

    private static final class FijosEnMemoria implements FixedExpenseRepository {

        private final List<FixedExpense> plantillas = new ArrayList<>();
        private final Set<String> aplicados = new HashSet<>();

        @Override
        public Optional<FixedExpense> findById(HouseholdId householdId, FixedExpenseId id) {
            return plantillas.stream()
                    .filter(f -> f.id().equals(id) && f.isAccessibleBy(householdId))
                    .findFirst();
        }

        @Override
        public List<FixedExpense> findAll(HouseholdId householdId) {
            return plantillas.stream().filter(f -> f.isAccessibleBy(householdId)).toList();
        }

        @Override
        public Set<FixedExpenseId> findAppliedIn(HouseholdId householdId, YearMonth month) {
            return plantillas.stream()
                    .filter(f -> f.isAccessibleBy(householdId))
                    .map(FixedExpense::id)
                    .filter(id -> aplicados.contains(clave(id, month)))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        /** Se comporta como la clave primaria real: el segundo en llegar recibe false. */
        @Override
        public boolean claimFor(FixedExpenseId id, YearMonth month) {
            return aplicados.add(clave(id, month));
        }

        @Override
        public FixedExpense save(FixedExpense fixedExpense) {
            plantillas.removeIf(f -> f.id().equals(fixedExpense.id()));
            plantillas.add(fixedExpense);
            return fixedExpense;
        }

        @Override
        public void delete(HouseholdId householdId, FixedExpenseId id) {
            plantillas.removeIf(f -> f.id().equals(id) && f.isAccessibleBy(householdId));
            // Las marcas caen con la plantilla, como el ON DELETE CASCADE real.
            aplicados.removeIf(k -> k.startsWith(id.toString() + "@"));
        }

        private static String clave(FixedExpenseId id, YearMonth month) {
            return id + "@" + month;
        }
    }

    private static final class GastosEnMemoria implements ExpenseRepository {

        private final List<Expense> gastos = new ArrayList<>();

        @Override
        public Optional<Expense> findById(HouseholdId householdId, ExpenseId expenseId) {
            return gastos.stream()
                    .filter(g -> g.id().equals(expenseId) && g.isAccessibleBy(householdId))
                    .findFirst();
        }

        @Override
        public List<Expense> findByMonth(HouseholdId householdId, YearMonth month) {
            return gastos.stream()
                    .filter(g -> g.isAccessibleBy(householdId) && g.belongsTo(month))
                    .toList();
        }

        @Override
        public List<Expense> findRecurringCommitments(HouseholdId householdId) {
            return gastos.stream()
                    .filter(g -> g.isAccessibleBy(householdId) && g.isStableCommitment())
                    .toList();
        }

        @Override
        public Expense save(Expense expense) {
            gastos.removeIf(g -> g.id().equals(expense.id()));
            gastos.add(expense);
            return expense;
        }

        @Override
        public void delete(HouseholdId householdId, ExpenseId expenseId) {
            gastos.removeIf(g -> g.id().equals(expenseId) && g.isAccessibleBy(householdId));
        }

        List<Expense> delMes(YearMonth month) {
            return gastos.stream().filter(g -> g.belongsTo(month)).toList();
        }
    }
}
