package com.gastos.mortgage.infrastructure.persistence.jpa;

import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.port.MortgageScenarioRepository;
import com.gastos.shared.domain.HouseholdId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida de los escenarios guardados sobre Spring Data JPA. */
@Repository
@Transactional
public class MortgageScenarioRepositoryAdapter implements MortgageScenarioRepository {

    private final MortgageScenarioJpaRepository jpaRepository;

    public MortgageScenarioRepositoryAdapter(MortgageScenarioJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MortgageScenario> findById(HouseholdId householdId, UUID scenarioId) {
        return jpaRepository.findByIdAndHouseholdId(scenarioId, householdId.value())
                .map(MortgageJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MortgageScenario> findAllByHousehold(HouseholdId householdId) {
        return jpaRepository.findByHouseholdIdOrderByUpdatedAtDesc(householdId.value()).stream()
                .map(MortgageJpaMapper::toDomain)
                .toList();
    }

    @Override
    public MortgageScenario save(MortgageScenario scenario) {
        jpaRepository.save(MortgageJpaMapper.toEntity(scenario));
        return scenario;
    }

    @Override
    public void delete(HouseholdId householdId, UUID scenarioId) {
        jpaRepository.deleteByIdAndHouseholdId(scenarioId, householdId.value());
    }
}
