package com.gastos.expenses.application;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.expenses.domain.model.MonthlySpendingReport;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.ResourceNotFoundException;
import java.time.YearMonth;
import java.util.List;

/**
 * Casos de uso del contexto de gastos: el CRUD completo mas el resumen mensual.
 *
 * <p>La clase orquesta y no calcula. El total del mes, el prorrateo de las
 * periodicidades y la deteccion de compromisos estables son responsabilidad de
 * {@link MonthlySpendingReport}, en el dominio.</p>
 *
 * <p>Todas las operaciones sobre un gasto concreto exigen {@link HouseholdId} y
 * vuelven a comprobar la pertenencia sobre el agregado recuperado: doble barrera
 * frente a BOLA/IDOR (OWASP API1).</p>
 */
public class ManageExpensesUseCase {

    private final ExpenseRepository repository;
    private final ManageFixedExpensesUseCase fixedExpenses;

    public ManageExpensesUseCase(ExpenseRepository repository,
                                 ManageFixedExpensesUseCase fixedExpenses) {
        this.repository = Guard.notNull(repository, "repository");
        this.fixedExpenses = Guard.notNull(fixedExpenses, "fixedExpenses");
    }

    public Expense register(RegisterExpenseCommand command) {
        Guard.notNull(command, "command");
        Expense expense = Expense.register(
                command.householdId(),
                command.registeredBy(),
                command.description(),
                command.amount(),
                command.category(),
                command.recurrence(),
                command.incurredOn(),
                command.accountId());
        return repository.save(expense);
    }

    public Expense update(HouseholdId householdId, ExpenseId expenseId, UpdateExpenseCommand command) {
        Guard.notNull(command, "command");
        Expense expense = requireOwned(householdId, expenseId);
        expense.correct(command.description(), command.amount(), command.incurredOn());
        expense.recategorize(command.category());
        expense.changeRecurrence(command.recurrence());
        return repository.save(expense);
    }

    public Expense findById(HouseholdId householdId, ExpenseId expenseId) {
        return requireOwned(householdId, expenseId);
    }

    /**
     * Gastos del mes, con los fijos ya incluidos.
     *
     * <p>Antes de leer se expanden los gastos fijos que falten
     * ({@link ManageFixedExpensesUseCase#expand}). Es una escritura dentro de una
     * lectura, y es deliberado: la alternativa era una tarea programada, y la API vive en
     * una instancia que duerme cuando nadie la usa, asi que el dia 1 a las 00:00 no habria
     * nadie para ejecutarla. Abrir el mes es la unica senal fiable de que alguien quiere
     * ver ese mes.</p>
     *
     * <p>Es idempotente, asi que recargar la pantalla no duplica nada.</p>
     */
    public List<Expense> listByMonth(HouseholdId householdId, YearMonth month) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(month, "month");
        fixedExpenses.expand(householdId, month);
        return repository.findByMonth(householdId, month);
    }

    public MonthlySpendingReport summarize(HouseholdId householdId, YearMonth month) {
        return MonthlySpendingReport.of(month, listByMonth(householdId, month));
    }

    public void delete(HouseholdId householdId, ExpenseId expenseId) {
        requireOwned(householdId, expenseId);
        repository.delete(householdId, expenseId);
    }

    /**
     * Recupera el gasto comprobando que pertenece al hogar que lo pide.
     *
     * <p>El mensaje es el mismo tanto si el gasto no existe como si es de otro hogar:
     * distinguirlos permitiria enumerar identificadores ajenos.</p>
     */
    private Expense requireOwned(HouseholdId householdId, ExpenseId expenseId) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(expenseId, "expenseId");
        Expense expense = repository.findById(householdId, expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));
        if (!expense.isAccessibleBy(householdId)) {
            throw new ResourceNotFoundException("Gasto no encontrado");
        }
        return expense;
    }
}
