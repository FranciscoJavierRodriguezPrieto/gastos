package com.gastos.mortgage.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data del catalogo. Toda consulta va acotada por hogar. */
public interface AidProgramJpaRepository extends JpaRepository<AidProgramEntity, UUID> {

    Optional<AidProgramEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    /** Los activos primero y, dentro de cada grupo, el que mas financia. */
    List<AidProgramEntity> findByHouseholdIdOrderByActiveDescMaxLoanToValueDesc(UUID householdId);

    void deleteByIdAndHouseholdId(UUID id, UUID householdId);
}
