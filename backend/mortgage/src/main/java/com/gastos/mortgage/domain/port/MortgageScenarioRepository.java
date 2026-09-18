package com.gastos.mortgage.domain.port;

import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.shared.domain.HouseholdId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para los escenarios guardados. Siempre acotado por hogar. */
public interface MortgageScenarioRepository {

    Optional<MortgageScenario> findById(HouseholdId householdId, UUID scenarioId);

    List<MortgageScenario> findAllByHousehold(HouseholdId householdId);

    MortgageScenario save(MortgageScenario scenario);

    void delete(HouseholdId householdId, UUID scenarioId);
}
