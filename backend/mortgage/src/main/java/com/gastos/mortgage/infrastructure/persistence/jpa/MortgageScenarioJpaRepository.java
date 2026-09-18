package com.gastos.mortgage.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de escenarios. Toda consulta va acotada por hogar. */
public interface MortgageScenarioJpaRepository extends JpaRepository<MortgageScenarioEntity, UUID> {

    Optional<MortgageScenarioEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    List<MortgageScenarioEntity> findByHouseholdIdOrderByUpdatedAtDesc(UUID householdId);

    void deleteByIdAndHouseholdId(UUID id, UUID householdId);
}
