package com.gastos.mortgage.infrastructure.persistence.jpa;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.port.AidProgramRepository;
import com.gastos.shared.domain.HouseholdId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida del catalogo de programas sobre Spring Data JPA. */
@Repository
@Transactional
public class AidProgramRepositoryAdapter implements AidProgramRepository {

    private final AidProgramJpaRepository jpaRepository;

    public AidProgramRepositoryAdapter(AidProgramJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AidProgram> findById(HouseholdId householdId, UUID programId) {
        return jpaRepository.findByIdAndHouseholdId(programId, householdId.value())
                .map(MortgageJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AidProgram> findAllByHousehold(HouseholdId householdId) {
        return jpaRepository
                .findByHouseholdIdOrderByActiveDescMaxLoanToValueDesc(householdId.value()).stream()
                .map(MortgageJpaMapper::toDomain)
                .toList();
    }

    @Override
    public AidProgram save(AidProgram program) {
        jpaRepository.save(MortgageJpaMapper.toEntity(program));
        return program;
    }

    @Override
    public void delete(HouseholdId householdId, UUID programId) {
        jpaRepository.deleteByIdAndHouseholdId(programId, householdId.value());
    }
}
