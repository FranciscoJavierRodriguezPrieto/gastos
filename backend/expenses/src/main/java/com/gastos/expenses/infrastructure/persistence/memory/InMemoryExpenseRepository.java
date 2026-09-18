package com.gastos.expenses.infrastructure.persistence.memory;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.shared.domain.HouseholdId;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia en memoria.
 *
 * <p><strong>Provisional:</strong> existe para que la API funcione de extremo a extremo
 * antes de que llegue PostgreSQL en {@code feature/persistence-postgresql}. Los datos
 * se pierden al reiniciar.</p>
 *
 * <p>Que sustituirlo por el adaptador JPA no obligue a tocar ni el dominio ni los casos
 * de uso es justamente la prueba de que la inversion de dependencias esta bien hecha.</p>
 */
@Repository
public class InMemoryExpenseRepository implements ExpenseRepository {

    private final Map<ExpenseId, Expense> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Expense> findById(HouseholdId householdId, ExpenseId expenseId) {
        // El filtro por hogar se aplica aqui tambien, no solo en el caso de uso: el
        // puerto promete aislamiento y el adaptador tiene que cumplirlo.
        return Optional.ofNullable(store.get(expenseId))
                .filter(expense -> expense.isAccessibleBy(householdId));
    }

    @Override
    public List<Expense> findByMonth(HouseholdId householdId, YearMonth month) {
        return store.values().stream()
                .filter(expense -> expense.isAccessibleBy(householdId))
                .filter(expense -> expense.belongsTo(month))
                .sorted(Comparator.comparing(Expense::incurredOn).reversed())
                .toList();
    }

    @Override
    public List<Expense> findRecurringCommitments(HouseholdId householdId) {
        return store.values().stream()
                .filter(expense -> expense.isAccessibleBy(householdId))
                .filter(Expense::isStableCommitment)
                .toList();
    }

    @Override
    public Expense save(Expense expense) {
        store.put(expense.id(), expense);
        return expense;
    }

    @Override
    public void delete(HouseholdId householdId, ExpenseId expenseId) {
        findById(householdId, expenseId).ifPresent(expense -> store.remove(expense.id()));
    }
}
