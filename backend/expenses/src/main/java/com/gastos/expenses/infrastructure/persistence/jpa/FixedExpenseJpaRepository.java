package com.gastos.expenses.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de los gastos fijos. Toda consulta va acotada por hogar. */
public interface FixedExpenseJpaRepository extends JpaRepository<FixedExpenseEntity, UUID> {

    Optional<FixedExpenseEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<FixedExpenseEntity> findByHouseholdIdOrderByDescriptionAsc(UUID householdId);

    void deleteByIdAndHouseholdId(UUID id, UUID householdId);
}
