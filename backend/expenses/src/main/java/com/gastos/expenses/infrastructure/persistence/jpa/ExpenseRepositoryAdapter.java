package com.gastos.expenses.infrastructure.persistence.jpa;

import com.gastos.expenses.domain.model.Expense;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.shared.domain.HouseholdId;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida del contexto de gastos sobre Spring Data JPA. */
@Repository
@Transactional
public class ExpenseRepositoryAdapter implements ExpenseRepository {

    private final ExpenseJpaRepository jpaRepository;

    public ExpenseRepositoryAdapter(ExpenseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Expense> findById(HouseholdId householdId, ExpenseId expenseId) {
        return jpaRepository.findByIdAndHouseholdId(expenseId.value(), householdId.value())
                .map(ExpenseJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Expense> findByMonth(HouseholdId householdId, YearMonth month) {
        return jpaRepository.findByHouseholdIdAndIncurredOnBetweenOrderByIncurredOnDesc(
                        householdId.value(), month.atDay(1), month.atEndOfMonth()).stream()
                .map(ExpenseJpaMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Expense> findRecurringCommitments(HouseholdId householdId) {
        // Que categorias cuentan como compromiso estable lo decide el dominio; la
        // consulta solo recibe la lista ya resuelta.
        List<String> categories = Arrays.stream(ExpenseCategory.values())
                .filter(ExpenseCategory::isRecurringCommitment)
                .map(Enum::name)
                .toList();

        return jpaRepository.findRecurringCommitments(householdId.value(), categories).stream()
                .map(ExpenseJpaMapper::toDomain)
                .toList();
    }

    @Override
    public Expense save(Expense expense) {
        jpaRepository.save(ExpenseJpaMapper.toEntity(expense));
        return expense;
    }

    @Override
    public void delete(HouseholdId householdId, ExpenseId expenseId) {
        jpaRepository.deleteByIdAndHouseholdId(expenseId.value(), householdId.value());
    }
}
