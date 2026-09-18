package com.gastos.mortgage.application;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.AmortizationSchedule;
import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.port.AidProgramRepository;
import com.gastos.mortgage.domain.port.MortgageScenarioRepository;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de la herramienta de hipoteca: simular al vuelo y gestionar los
 * escenarios guardados.
 *
 * <p>La capa de aplicacion solo orquesta: comprueba la autorizacion, recupera el
 * catalogo de programas del hogar y delega el calculo en el dominio. No contiene una
 * sola regla financiera.</p>
 */
public class SimulateMortgageUseCase {

    private final MortgageSimulator simulator;
    private final MortgageScenarioRepository scenarioRepository;
    private final AidProgramRepository aidProgramRepository;
    private final Clock clock;

    public SimulateMortgageUseCase(MortgageSimulator simulator,
                                   MortgageScenarioRepository scenarioRepository,
                                   AidProgramRepository aidProgramRepository,
                                   Clock clock) {
        this.simulator = Guard.notNull(simulator, "simulator");
        this.scenarioRepository = Guard.notNull(scenarioRepository, "scenarioRepository");
        this.aidProgramRepository = Guard.notNull(aidProgramRepository, "aidProgramRepository");
        this.clock = Guard.notNull(clock, "clock");
    }

    /** Simulacion efimera: no toca el almacenamiento. Es la que mueven los deslizadores. */
    public SimulationResult simulate(HouseholdId householdId, SimulationRequest request) {
        Guard.notNull(householdId, "householdId");
        return simulator.simulate(request, programsOf(householdId));
    }

    /** Cuadro de amortizacion completo del escenario. */
    public AmortizationSchedule schedule(HouseholdId householdId, SimulationRequest request) {
        Guard.notNull(householdId, "householdId");
        return simulator.scheduleFor(request, programsOf(householdId));
    }

    public MortgageScenario saveScenario(HouseholdId householdId, String name,
                                         SimulationRequest request) {
        Guard.notNull(householdId, "householdId");
        MortgageScenario scenario =
                MortgageScenario.create(householdId, name, request, clock.instant());
        return scenarioRepository.save(scenario);
    }

    public MortgageScenario updateScenario(HouseholdId householdId, UUID scenarioId, String name,
                                           SimulationRequest request) {
        MortgageScenario scenario = requireOwned(householdId, scenarioId);
        scenario.rename(name, clock.instant());
        scenario.updateRequest(request, clock.instant());
        return scenarioRepository.save(scenario);
    }

    public MortgageScenario findScenario(HouseholdId householdId, UUID scenarioId) {
        return requireOwned(householdId, scenarioId);
    }

    /**
     * Recupera un escenario guardado y lo recalcula con el motor y el catalogo vigentes.
     *
     * <p>Se guarda la entrada, nunca el resultado. Si el hogar corrige las condiciones de
     * un programa, los escenarios guardados reflejan la correccion al abrirlos; devolver
     * una cuota calculada hace meses seria devolver un dato falso.</p>
     */
    public SimulationResult replayScenario(HouseholdId householdId, UUID scenarioId) {
        MortgageScenario scenario = requireOwned(householdId, scenarioId);
        return simulator.simulate(scenario.request(), programsOf(householdId));
    }

    public List<MortgageScenario> listScenarios(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return scenarioRepository.findAllByHousehold(householdId);
    }

    public void deleteScenario(HouseholdId householdId, UUID scenarioId) {
        requireOwned(householdId, scenarioId);
        scenarioRepository.delete(householdId, scenarioId);
    }

    private List<AidProgram> programsOf(HouseholdId householdId) {
        return aidProgramRepository.findAllByHousehold(householdId);
    }

    /**
     * La comprobacion de pertenencia se hace sobre el agregado recuperado y no solo en
     * la consulta: doble barrera frente a BOLA/IDOR (OWASP API1).
     */
    private MortgageScenario requireOwned(HouseholdId householdId, UUID scenarioId) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(scenarioId, "scenarioId");
        MortgageScenario scenario = scenarioRepository.findById(householdId, scenarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Escenario no encontrado"));
        if (!scenario.isAccessibleBy(householdId)) {
            throw new ResourceNotFoundException("Escenario no encontrado");
        }
        return scenario;
    }
}
