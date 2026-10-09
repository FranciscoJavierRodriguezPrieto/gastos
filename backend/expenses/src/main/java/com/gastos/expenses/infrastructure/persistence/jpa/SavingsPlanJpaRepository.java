package com.gastos.expenses.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data. La clave es el propio hogar. */
public interface SavingsPlanJpaRepository extends JpaRepository<SavingsPlanEntity, UUID> {
}
