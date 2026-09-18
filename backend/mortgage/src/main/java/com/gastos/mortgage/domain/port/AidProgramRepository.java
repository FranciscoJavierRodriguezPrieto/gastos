package com.gastos.mortgage.domain.port;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.shared.domain.HouseholdId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida del catalogo de programas de ayuda.
 *
 * <p>El catalogo se guarda por hogar y no de forma global: asi cada hogar afina las
 * cifras segun lo que le confirme su entidad o el boletin oficial, sin pisar al otro y
 * sin necesitar un modelo de permisos aparte para datos compartidos.</p>
 */
public interface AidProgramRepository {

    Optional<AidProgram> findById(HouseholdId householdId, UUID programId);

    List<AidProgram> findAllByHousehold(HouseholdId householdId);

    AidProgram save(AidProgram program);

    void delete(HouseholdId householdId, UUID programId);
}
