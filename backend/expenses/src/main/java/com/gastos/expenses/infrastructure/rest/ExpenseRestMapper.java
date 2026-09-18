package com.gastos.expenses.infrastructure.rest;

import com.gastos.expenses.application.RegisterExpenseCommand;
import com.gastos.expenses.application.UpdateExpenseCommand;
import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.MonthlySpendingReport;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseRequest;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseResponse;
import com.gastos.expenses.infrastructure.rest.dto.MonthlySummaryResponse;
import com.gastos.expenses.infrastructure.rest.dto.MonthlySummaryResponse.CategoryAmountResponse;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Traduce entre el contrato HTTP y el dominio.
 *
 * <p>Es el unico punto del contexto que conoce ambos lados. Concentrar aqui la
 * traduccion tiene dos efectos practicos: el dominio puede evolucionar sin romper a
 * los clientes, y cuando un campo aparece mal en la API se sabe exactamente en que
 * fichero mirar.</p>
 *
 * <p>Clase sin estado y sin dependencias: se testea con llamadas directas, sin Spring.</p>
 */
public final class ExpenseRestMapper {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ExpenseRestMapper() {
    }

    public static RegisterExpenseCommand toCommand(HouseholdId householdId, UserId registeredBy,
                                                   ExpenseRequest request) {
        return new RegisterExpenseCommand(
                householdId,
                registeredBy,
                request.description(),
                Money.euros(request.amount()),
                parseCategory(request.category()),
                parseRecurrence(request.recurrence()),
                request.incurredOn(),
                request.accountId());
    }

    public static UpdateExpenseCommand toUpdateCommand(ExpenseRequest request) {
        return new UpdateExpenseCommand(
                request.description(),
                Money.euros(request.amount()),
                parseCategory(request.category()),
                parseRecurrence(request.recurrence()),
                request.incurredOn());
    }

    public static ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
                expense.id().value(),
                expense.description(),
                expense.amount().amount(),
                expense.category().name(),
                expense.category().displayName(),
                expense.recurrence().name(),
                expense.incurredOn(),
                expense.accountId(),
                expense.monthlyEquivalent().amount(),
                expense.isStableCommitment());
    }

    public static List<ExpenseResponse> toResponses(List<Expense> expenses) {
        return expenses.stream().map(ExpenseRestMapper::toResponse).toList();
    }

    public static MonthlySummaryResponse toSummary(MonthlySpendingReport report) {
        List<CategoryAmountResponse> breakdown = report.byCategory().entrySet().stream()
                .map(entry -> new CategoryAmountResponse(
                        entry.getKey().name(),
                        entry.getKey().displayName(),
                        entry.getValue().amount(),
                        share(entry.getValue(), report.total())))
                // De mayor a menor gasto: el desglose del dashboard se lee de un vistazo.
                .sorted(Comparator.comparing(CategoryAmountResponse::amount).reversed())
                .toList();

        return new MonthlySummaryResponse(
                report.month().toString(),
                report.total().amount(),
                report.monthlyCommitments().amount(),
                breakdown);
    }

    private static BigDecimal share(Money amount, Money total) {
        if (total.isZero()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.amount()
                .multiply(HUNDRED)
                .divide(total.amount(), 2, RoundingMode.HALF_UP);
    }

    private static ExpenseCategory parseCategory(String value) {
        try {
            return ExpenseCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Categoria no valida. Valores admitidos: "
                    + names(ExpenseCategory.values()), e);
        }
    }

    private static Recurrence parseRecurrence(String value) {
        try {
            return Recurrence.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Periodicidad no valida. Valores admitidos: "
                    + names(Recurrence.values()), e);
        }
    }

    private static String names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }
}
