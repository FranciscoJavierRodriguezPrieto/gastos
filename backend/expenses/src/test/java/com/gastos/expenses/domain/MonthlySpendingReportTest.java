package com.gastos.expenses.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.MonthlySpendingReport;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Resumen mensual de gastos")
class MonthlySpendingReportTest {

    private static final HouseholdId HOUSEHOLD = HouseholdId.newId();
    private static final UserId USER = UserId.newId();
    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private static Expense expense(String description, String amount, ExpenseCategory category,
                                   Recurrence recurrence, LocalDate date) {
        return Expense.register(HOUSEHOLD, USER, description, Money.euros(amount), category, recurrence,
                date, null);
    }

    @Test
    @DisplayName("agrega el total del mes y lo desglosa por categoria")
    void aggregatesByCategory() {
        List<Expense> expenses = List.of(
                expense("Compra semanal", "180.50", ExpenseCategory.ALIMENTACION, Recurrence.PUNTUAL,
                        LocalDate.of(2026, 3, 4)),
                expense("Compra semanal", "142.30", ExpenseCategory.ALIMENTACION, Recurrence.PUNTUAL,
                        LocalDate.of(2026, 3, 11)),
                expense("Cine", "27.00", ExpenseCategory.OCIO, Recurrence.PUNTUAL,
                        LocalDate.of(2026, 3, 15)));

        MonthlySpendingReport report = MonthlySpendingReport.of(MARCH, expenses);

        assertThat(report.total()).isEqualTo(Money.euros("349.80"));
        assertThat(report.amountFor(ExpenseCategory.ALIMENTACION)).isEqualTo(Money.euros("322.80"));
        assertThat(report.amountFor(ExpenseCategory.OCIO)).isEqualTo(Money.euros("27.00"));
        assertThat(report.amountFor(ExpenseCategory.SALUD)).isEqualTo(Money.zero());
    }

    @Test
    @DisplayName("ignora los gastos de otros meses")
    void ignoresOtherMonths() {
        List<Expense> expenses = List.of(
                expense("Gasolina", "60.00", ExpenseCategory.COCHE, Recurrence.PUNTUAL,
                        LocalDate.of(2026, 3, 2)),
                expense("Gasolina", "65.00", ExpenseCategory.COCHE, Recurrence.PUNTUAL,
                        LocalDate.of(2026, 4, 2)));

        MonthlySpendingReport report = MonthlySpendingReport.of(MARCH, expenses);

        assertThat(report.total()).isEqualTo(Money.euros("60.00"));
    }

    @Test
    @DisplayName("solo los compromisos recurrentes computan como deuda para el DTI")
    void onlyStableCommitmentsCountAsDebt() {
        List<Expense> expenses = List.of(
                expense("Prestamo coche", "225.00", ExpenseCategory.COCHE, Recurrence.MENSUAL,
                        LocalDate.of(2026, 3, 1)),
                expense("Seguro hogar", "300.00", ExpenseCategory.SEGUROS, Recurrence.ANUAL,
                        LocalDate.of(2026, 3, 10)),
                expense("Concierto", "80.00", ExpenseCategory.OCIO, Recurrence.PUNTUAL,
                        LocalDate.of(2026, 3, 20)));

        MonthlySpendingReport report = MonthlySpendingReport.of(MARCH, expenses);

        // 225 al mes + 300 anuales prorrateados (25 al mes) = 250. El ocio puntual no computa.
        assertThat(report.monthlyCommitments().amount().doubleValue()).isCloseTo(250.00,
                org.assertj.core.data.Offset.offset(0.05));
        assertThat(report.total()).isEqualTo(Money.euros("605.00"));
    }

    @Test
    @DisplayName("calcula el superavit del mes frente a los ingresos del hogar")
    void computesSurplus() {
        List<Expense> expenses = List.of(
                expense("Alquiler", "1200.00", ExpenseCategory.VIVIENDA, Recurrence.MENSUAL,
                        LocalDate.of(2026, 3, 1)));

        MonthlySpendingReport report = MonthlySpendingReport.of(MARCH, expenses);

        assertThat(report.surplusAgainst(Money.euros(3_400))).isEqualTo(Money.euros("2200.00"));
    }

    @Test
    @DisplayName("rechaza registrar un gasto con importe negativo")
    void rejectsNegativeExpense() {
        assertThatThrownBy(() -> expense("Devolucion", "-10.00", ExpenseCategory.OTROS,
                Recurrence.PUNTUAL, LocalDate.of(2026, 3, 1)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("positivo");
    }
}
