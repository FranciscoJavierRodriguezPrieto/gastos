package com.gastos.iam.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de hogares. */
public interface HouseholdJpaRepository extends JpaRepository<HouseholdEntity, UUID> {
}
