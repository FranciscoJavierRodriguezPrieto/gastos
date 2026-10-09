package com.gastos.expenses.infrastructure.persistence.jpa;

import com.gastos.expenses.domain.port.SavingsPlanRepository;
import com.gastos.shared.domain.HouseholdId;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida de la prevision de ahorro sobre Spring Data JPA. */
@Repository
@Transactional
public class SavingsPlanRepositoryAdapter implements SavingsPlanRepository {

    private final SavingsPlanJpaRepository jpaRepository;

    public SavingsPlanRepositoryAdapter(SavingsPlanJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> find(HouseholdId householdId) {
        return jpaRepository.findById(householdId.value()).map(SavingsPlanEntity::getDocument);
    }

    @Override
    public void save(HouseholdId householdId, String document) {
        jpaRepository.save(new SavingsPlanEntity(householdId.value(), document));
    }
}
