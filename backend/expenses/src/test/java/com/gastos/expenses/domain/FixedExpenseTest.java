package com.gastos.expenses.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gasto fijo")
class FixedExpenseTest {

    private static final YearMonth ENERO = YearMonth.of(2026, 1);
    private final HouseholdId hogar = HouseholdId.newId();
    private final UserId usuario = UserId.newId();

    private FixedExpense alquiler(YearMonth desde) {
        return FixedExpense.create(hogar, usuario, "Alquiler", Money.euros(800),
                ExpenseCategory.VIVIENDA, 1, null, desde);
    }

    @Test
    @DisplayName("genera desde su mes de alta en adelante, nunca antes")
    void appliesFromStartMonthOnwards() {
        FixedExpense fijo = alquiler(ENERO);

        assertThat(fijo.appliesTo(ENERO.minusMonths(1))).isFalse();
        assertThat(fijo.appliesTo(ENERO)).isTrue();
        assertThat(fijo.appliesTo(ENERO.plusMonths(12))).isTrue();
    }

    @Test
    @DisplayName("al darlo de baja deja de generar desde ese mes, incluido")
    void stopsGeneratingFromEndMonth() {
        FixedExpense fijo = alquiler(ENERO);
        fijo.discontinueFrom(YearMonth.of(2026, 4));

        assertThat(fijo.appliesTo(YearMonth.of(2026, 3))).isTrue();
        assertThat(fijo.appliesTo(YearMonth.of(2026, 4))).isFalse();
        assertThat(fijo.isActive()).isFalse();
    }

    @Test
    @DisplayName("el gasto generado nace mensual y atado a su plantilla")
    void materialisedExpenseIsMonthlyAndLinked() {
        FixedExpense fijo = alquiler(ENERO);

        Expense gasto = fijo.materialiseFor(YearMonth.of(2026, 3));

        assertThat(gasto.recurrence()).isEqualTo(Recurrence.MENSUAL);
        assertThat(gasto.fixedExpenseId()).isEqualTo(fijo.id());
        assertThat(gasto.comesFromFixedExpense()).isTrue();
        assertThat(gasto.incurredOn()).isEqualTo(java.time.LocalDate.of(2026, 3, 1));
        assertThat(gasto.amount()).isEqualTo(Money.euros(800));
        // Mensual y de una categoria que la banca computa: cuenta para el DTI.
        assertThat(gasto.isStableCommitment()).isTrue();
    }

    /**
     * El nucleo del diseno. Cambiar la plantilla no puede reescribir lo que ya se genero:
     * si el alquiler sube, los meses pasados conservan lo que de verdad se pago.
     */
    @Test
    @DisplayName("subir el importe no toca los gastos ya generados")
    void updatingTheTemplateDoesNotRewriteHistory() {
        FixedExpense fijo = alquiler(ENERO);
        Expense enero = fijo.materialiseFor(ENERO);

        fijo.update("Alquiler", Money.euros(850), ExpenseCategory.VIVIENDA, 1);
        Expense marzo = fijo.materialiseFor(YearMonth.of(2026, 3));

        assertThat(enero.amount()).isEqualTo(Money.euros(800));
        assertThat(marzo.amount()).isEqualTo(Money.euros(850));
    }

    @Test
    @DisplayName("no genera en un mes en el que no esta vigente")
    void cannotMaterialiseOutsideValidity() {
        FixedExpense fijo = alquiler(ENERO);

        assertThatThrownBy(() -> fijo.materialiseFor(ENERO.minusMonths(1)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no esta vigente");
    }

    @Test
    @DisplayName("el dia de cargo se limita a 28 para que exista en febrero")
    void dayOfMonthIsCappedAt28() {
        assertThatThrownBy(() -> FixedExpense.create(hogar, usuario, "Cuota", Money.euros(30),
                ExpenseCategory.OCIO, 31, null, ENERO))
                .isInstanceOf(DomainException.class);

        FixedExpense elDia28 = FixedExpense.create(hogar, usuario, "Cuota", Money.euros(30),
                ExpenseCategory.OCIO, 28, null, ENERO);
        assertThat(elDia28.materialiseFor(YearMonth.of(2026, 2)).incurredOn())
                .isEqualTo(java.time.LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("un importe cero o negativo se rechaza")
    void amountMustBePositive() {
        assertThatThrownBy(() -> FixedExpense.create(hogar, usuario, "Nada", Money.zero(),
                ExpenseCategory.OTROS, 1, null, ENERO))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    @DisplayName("la baja no puede ser anterior al alta")
    void cannotEndBeforeItStarts() {
        FixedExpense fijo = alquiler(ENERO);

        assertThatThrownBy(() -> fijo.discontinueFrom(ENERO.minusMonths(1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("reactivarlo lo devuelve a vigente")
    void reactivating() {
        FixedExpense fijo = alquiler(ENERO);
        fijo.discontinueFrom(YearMonth.of(2026, 4));

        fijo.reactivate();

        assertThat(fijo.isActive()).isTrue();
        assertThat(fijo.appliesTo(YearMonth.of(2026, 9))).isTrue();
    }

    @Test
    @DisplayName("un gasto fijo de otro hogar no es accesible")
    void isolatedByHousehold() {
        assertThat(alquiler(ENERO).isAccessibleBy(HouseholdId.newId())).isFalse();
        assertThat(alquiler(ENERO).isAccessibleBy(hogar)).isTrue();
    }
}
