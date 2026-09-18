package com.gastos.mortgage.infrastructure.persistence.memory;

import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.port.MortgageScenarioRepository;
import com.gastos.shared.domain.HouseholdId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia en memoria.
 *
 * <p><strong>Provisional</strong> hasta {@code feature/persistence-postgresql}. Los
 * datos se pierden al reiniciar.</p>
 */
@Repository
public class InMemoryMortgageScenarioRepository implements MortgageScenarioRepository {

    private final Map<UUID, MortgageScenario> store = new ConcurrentHashMap<>();

    @Override
    public Optional<MortgageScenario> findById(HouseholdId householdId, UUID scenarioId) {
        return Optional.ofNullable(store.get(scenarioId))
                .filter(scenario -> scenario.isAccessibleBy(householdId));
    }

    @Override
    public List<MortgageScenario> findAllByHousehold(HouseholdId householdId) {
        return store.values().stream()
                .filter(scenario -> scenario.isAccessibleBy(householdId))
                .sorted(Comparator.comparing(MortgageScenario::updatedAt).reversed())
                .toList();
    }

    @Override
    public MortgageScenario save(MortgageScenario scenario) {
        store.put(scenario.id(), scenario);
        return scenario;
    }

    @Override
    public void delete(HouseholdId householdId, UUID scenarioId) {
        findById(householdId, scenarioId).ifPresent(scenario -> store.remove(scenario.id()));
    }
}
