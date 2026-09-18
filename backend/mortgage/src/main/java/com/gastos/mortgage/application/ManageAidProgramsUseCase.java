package com.gastos.mortgage.application;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.policy.ReferenceAidPrograms;
import com.gastos.mortgage.domain.port.AidProgramRepository;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import com.gastos.shared.domain.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso del catalogo de programas de ayuda.
 *
 * <p>Es un CRUD deliberadamente aburrido: la gracia del diseno es que la normativa vive
 * en la base de datos y no en el codigo, asi que mantenerla al dia no requiere
 * programador.</p>
 */
public class ManageAidProgramsUseCase {

    private final AidProgramRepository repository;

    public ManageAidProgramsUseCase(AidProgramRepository repository) {
        this.repository = Guard.notNull(repository, "repository");
    }

    public List<AidProgram> list(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return repository.findAllByHousehold(householdId);
    }

    public AidProgram findById(HouseholdId householdId, UUID programId) {
        return requireOwned(householdId, programId);
    }

    public AidProgram create(HouseholdId householdId, String name, Percentage maxLoanToValue,
                             Money maxPropertyPrice, Integer maxApplicantAge,
                             boolean requiresFirstHome, boolean active, String sourceNote) {
        Guard.notNull(householdId, "householdId");
        return repository.save(AidProgram.create(householdId, name, maxLoanToValue, maxPropertyPrice,
                maxApplicantAge, requiresFirstHome, active, sourceNote));
    }

    public AidProgram update(HouseholdId householdId, UUID programId, String name,
                             Percentage maxLoanToValue, Money maxPropertyPrice,
                             Integer maxApplicantAge, boolean requiresFirstHome, boolean active,
                             String sourceNote) {
        AidProgram program = requireOwned(householdId, programId);
        program.update(name, maxLoanToValue, maxPropertyPrice, maxApplicantAge, requiresFirstHome,
                active, sourceNote);
        return repository.save(program);
    }

    public void delete(HouseholdId householdId, UUID programId) {
        requireOwned(householdId, programId);
        repository.delete(householdId, programId);
    }

    /**
     * Instala el catalogo de partida.
     *
     * <p>Es idempotente por nombre: volver a llamarlo no duplica los programas que ya
     * existan, para que el boton de la pantalla se pueda pulsar dos veces sin dejar el
     * catalogo hecho un desastre.</p>
     */
    public List<AidProgram> installReferenceCatalog(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        List<String> existingNames = list(householdId).stream().map(AidProgram::name).toList();

        ReferenceAidPrograms.installFor(householdId).stream()
                .filter(program -> !existingNames.contains(program.name()))
                .forEach(repository::save);

        return list(householdId);
    }

    private AidProgram requireOwned(HouseholdId householdId, UUID programId) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(programId, "programId");
        AidProgram program = repository.findById(householdId, programId)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado"));
        if (!program.isAccessibleBy(householdId)) {
            throw new ResourceNotFoundException("Programa no encontrado");
        }
        return program;
    }
}
