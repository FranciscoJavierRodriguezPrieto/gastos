package com.gastos.mortgage.infrastructure.persistence.memory;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.port.AidProgramRepository;
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
public class InMemoryAidProgramRepository implements AidProgramRepository {

    private final Map<UUID, AidProgram> store = new ConcurrentHashMap<>();

    @Override
    public Optional<AidProgram> findById(HouseholdId householdId, UUID programId) {
        return Optional.ofNullable(store.get(programId))
                .filter(program -> program.isAccessibleBy(householdId));
    }

    @Override
    public List<AidProgram> findAllByHousehold(HouseholdId householdId) {
        return store.values().stream()
                .filter(program -> program.isAccessibleBy(householdId))
                // Los activos primero y, dentro de cada grupo, el que mas financia.
                .sorted(Comparator.comparing(AidProgram::isActive).reversed()
                        .thenComparing(AidProgram::maxLoanToValue, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public AidProgram save(AidProgram program) {
        store.put(program.id(), program);
        return program;
    }

    @Override
    public void delete(HouseholdId householdId, UUID programId) {
        findById(householdId, programId).ifPresent(program -> store.remove(program.id()));
    }
}
