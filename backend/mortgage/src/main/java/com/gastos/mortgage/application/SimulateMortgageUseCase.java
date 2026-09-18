package com.gastos.mortgage.application;

import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.port.MortgageScenarioRepository;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de simulacion hipotecaria.
 *
 * <p>La capa de aplicacion solo orquesta: valida la autorizacion, recupera el agregado
 * y delega el calculo en el dominio. No contiene una sola regla financiera, de forma
 * que toda la logica de negocio sea testeable sin infraestructura.</p>
 *
 * <p>Sin anotaciones de framework: el cableado ocurre en el modulo bootstrap. Esto
 * mantiene el nucleo independiente de Spring y permite verificarlo con ArchUnit.</p>
 */
public class SimulateMortgageUseCase {

    private final MortgageSimulator simulator;
    private final MortgageScenarioRepository scenarioRepository;
    private final Clock clock;

    public SimulateMortgageUseCase(MortgageSimulator simulator,
                                   MortgageScenarioRepository scenarioRepository,
                                   Clock clock) {
        this.simulator = Guard.notNull(simulator, "simulator");
        this.scenarioRepository = Guard.notNull(scenarioRepository, "scenarioRepository");
        this.clock = Guard.notNull(clock, "clock");
    }

    /** Simulacion efimera: no toca la base de datos. */
    public SimulationResult simulate(SimulationRequest request) {
        return simulator.simulate(request);
    }

    /** Guarda el escenario y devuelve su resultado recalculado. */
    public SimulationResult saveScenario(HouseholdId householdId, String name,
                                         SimulationRequest request) {
        Guard.notNull(householdId, "householdId");
        MortgageScenario scenario =
                MortgageScenario.create(householdId, name, request, clock.instant());
        scenarioRepository.save(scenario);
        return simulator.simulate(request);
    }

    /**
     * Recupera un escenario guardado y lo recalcula.
     *
     * <p>La comprobacion de pertenencia se hace sobre el agregado recuperado y no solo
     * en la consulta: doble barrera frente a BOLA/IDOR (OWASP API1).</p>
     */
    public SimulationResult replayScenario(HouseholdId householdId, UUID scenarioId) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(scenarioId, "scenarioId");

        MortgageScenario scenario = scenarioRepository.findById(householdId, scenarioId)
                .orElseThrow(() -> new DomainException("Escenario no encontrado"));
        if (!scenario.isAccessibleBy(householdId)) {
            throw new DomainException("Escenario no encontrado");
        }
        return simulator.simulate(scenario.request());
    }

    public List<MortgageScenario> listScenarios(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return scenarioRepository.findAllByHousehold(householdId);
    }
}
