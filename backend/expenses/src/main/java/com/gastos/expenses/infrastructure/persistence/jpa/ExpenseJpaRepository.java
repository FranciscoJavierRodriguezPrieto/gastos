package com.gastos.expenses.infrastructure.persistence.jpa;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data. Toda consulta va acotada por hogar.
 *
 * <p>El filtro del mes se resuelve con un rango de fechas en lugar de con funciones
 * sobre la columna: asi la consulta puede usar el indice
 * {@code idx_expense_household_date} en vez de recorrer la tabla entera.</p>
 */
public interface ExpenseJpaRepository extends JpaRepository<ExpenseEntity, UUID> {

    Optional<ExpenseEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<ExpenseEntity> findByHouseholdIdAndIncurredOnBetweenOrderByIncurredOnDesc(
            UUID householdId, LocalDate from, LocalDate to);

    /**
     * Compromisos estables: gastos recurrentes de categorias que la banca computa como
     * deuda al estudiar una hipoteca.
     */
    @Query("""
            select e from ExpenseEntity e
            where e.householdId = :householdId
              and e.recurrence <> 'PUNTUAL'
              and e.category in :categories
            """)
    List<ExpenseEntity> findRecurringCommitments(@Param("householdId") UUID householdId,
                                                 @Param("categories") List<String> categories);

    void deleteByIdAndHouseholdId(UUID id, UUID householdId);
}
