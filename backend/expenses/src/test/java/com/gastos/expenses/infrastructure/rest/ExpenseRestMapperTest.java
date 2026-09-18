package com.gastos.expenses.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.expenses.application.RegisterExpenseCommand;
import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.MonthlySpendingReport;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseRequest;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseResponse;
import com.gastos.expenses.infrastructure.rest.dto.MonthlySummaryResponse;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Mapper REST de gastos")
class ExpenseRestMapperTest {

    private static final HouseholdId HOUSEHOLD = HouseholdId.newId();
    private static final UserId USER = UserId.newId();
    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    @Test
    @DisplayName("convierte el DTO de entrada a tipos de dominio")
    void mapsRequestToCommand() {
        ExpenseRequest request = new ExpenseRequest("Compra semanal", new BigDecimal("180.50"),
                "alimentacion", "puntual", LocalDate.of(2026, 3, 4), null);

        RegisterExpenseCommand command = ExpenseRestMapper.toCommand(HOUSEHOLD, USER, request);

        assertThat(command.amount()).isEqualTo(Money.euros("180.50"));
        assertThat(command.category()).isEqualTo(ExpenseCategory.ALIMENTACION);
        assertThat(command.recurrence()).isEqualTo(Recurrence.PUNTUAL);
        assertThat(command.householdId()).isEqualTo(HOUSEHOLD);
        assertThat(command.registeredBy()).isEqualTo(USER);
    }

    @Test
    @DisplayName("rechaza una categoria desconocida indicando los valores admitidos")
    void rejectsUnknownCategory() {
        ExpenseRequest request = new ExpenseRequest("Algo", new BigDecimal("10.00"),
                "criptomonedas", "puntual", LocalDate.of(2026, 3, 4), null);

        assertThatThrownBy(() -> ExpenseRestMapper.toCommand(HOUSEHOLD, USER, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("ALIMENTACION");
    }

    @Test
    @DisplayName("la respuesta incluye el equivalente mensual calculado en el dominio")
    void responseCarriesMonthlyEquivalent() {
        Expense expense = Expense.register(HOUSEHOLD, USER, "Seguro hogar", Money.euros("300.00"),
                ExpenseCategory.SEGUROS, Recurrence.ANUAL, LocalDate.of(2026, 3, 10), null);

        ExpenseResponse response = ExpenseRestMapper.toResponse(expense);

        assertThat(response.amount()).isEqualByComparingTo("300.00");
        assertThat(response.monthlyEquivalent().doubleValue()).isCloseTo(25.00,
                org.assertj.core.data.Offset.offset(0.05));
        assertThat(response.stableCommitment()).isTrue();
        assertThat(response.categoryLabel()).isEqualTo("Seguros");
    }

    @Test
    @DisplayName("el desglose del resumen va ordenado de mayor a menor con su peso relativo")
    void summaryIsSortedWithShares() {
        List<Expense> expenses = List.of(
                Expense.register(HOUSEHOLD, USER, "Cine", Money.euros("25.00"), ExpenseCategory.OCIO,
                        Recurrence.PUNTUAL, LocalDate.of(2026, 3, 15), null),
                Expense.register(HOUSEHOLD, USER, "Compra", Money.euros("75.00"),
                        ExpenseCategory.ALIMENTACION, Recurrence.PUNTUAL, LocalDate.of(2026, 3, 4), null));

        MonthlySummaryResponse summary =
                ExpenseRestMapper.toSummary(MonthlySpendingReport.of(MARCH, expenses));

        assertThat(summary.month()).isEqualTo("2026-03");
        assertThat(summary.total()).isEqualByComparingTo("100.00");
        assertThat(summary.byCategory()).hasSize(2);
        assertThat(summary.byCategory().get(0).category()).isEqualTo("ALIMENTACION");
        assertThat(summary.byCategory().get(0).share()).isEqualByComparingTo("75.00");
        assertThat(summary.byCategory().get(1).share()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("un mes sin gastos no revienta al calcular los porcentajes")
    void emptyMonthDoesNotDivideByZero() {
        MonthlySummaryResponse summary =
                ExpenseRestMapper.toSummary(MonthlySpendingReport.of(MARCH, List.of()));

        assertThat(summary.total()).isEqualByComparingTo("0.00");
        assertThat(summary.byCategory()).isEmpty();
    }
}
