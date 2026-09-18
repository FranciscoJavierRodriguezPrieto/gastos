package com.gastos.mortgage.infrastructure.rest;

import com.gastos.mortgage.application.SimulateMortgageUseCase;
import com.gastos.mortgage.domain.model.MortgageScenario;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.infrastructure.rest.dto.ScenarioRequestDto;
import com.gastos.mortgage.infrastructure.rest.dto.ScenarioResponseDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationRequestDto;
import com.gastos.mortgage.infrastructure.rest.dto.SimulationResponseDto;
import com.gastos.shared.domain.HouseholdId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador REST de la herramienta de hipoteca.
 *
 * <p>La simulacion es un POST y no un GET pese a no modificar nada: son una docena de
 * parametros, varios con decimales, y meterlos en la URL los dejaria escritos en los
 * registros de acceso y en el historial del navegador. Datos economicos del hogar no
 * viajan en la query string.</p>
 *
 * <p>La simulacion tambien exige el hogar porque el catalogo de programas de ayuda se
 * configura por hogar: sin saber quien pregunta no se puede decidir el LTV.</p>
 */
@RestController
@RequestMapping("/api/v1/mortgage")
public class MortgageController {

    private final SimulateMortgageUseCase useCase;

    public MortgageController(SimulateMortgageUseCase useCase) {
        this.useCase = useCase;
    }

    /** Simulacion efimera: es la que responde a cada movimiento de los deslizadores. */
    @PostMapping("/simulations")
    public SimulationResponseDto simulate(
            @RequestHeader("X-Household-Id") UUID householdId,
            @Valid @RequestBody SimulationRequestDto request) {

        return MortgageRestMapper.toResponse(useCase.simulate(
                new HouseholdId(householdId), MortgageRestMapper.toDomain(request)));
    }

    @GetMapping("/scenarios")
    public List<ScenarioResponseDto> listScenarios(@RequestHeader("X-Household-Id") UUID householdId) {
        HouseholdId household = new HouseholdId(householdId);
        return useCase.listScenarios(household).stream()
                .map(scenario -> MortgageRestMapper.toResponse(
                        scenario, useCase.simulate(household, scenario.request())))
                .toList();
    }

    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioResponseDto> saveScenario(
            @RequestHeader("X-Household-Id") UUID householdId,
            @Valid @RequestBody ScenarioRequestDto request) {

        HouseholdId household = new HouseholdId(householdId);
        SimulationRequest simulation = MortgageRestMapper.toDomain(request.simulation());
        MortgageScenario scenario = useCase.saveScenario(household, request.name(), simulation);

        ScenarioResponseDto response =
                MortgageRestMapper.toResponse(scenario, useCase.simulate(household, simulation));

        return ResponseEntity.created(URI.create("/api/v1/mortgage/scenarios/" + response.id()))
                .body(response);
    }

    /** Devuelve el escenario recalculado con las politicas vigentes, no con las de cuando se guardo. */
    @GetMapping("/scenarios/{id}")
    public ScenarioResponseDto findScenario(
            @RequestHeader("X-Household-Id") UUID householdId,
            @PathVariable UUID id) {

        HouseholdId household = new HouseholdId(householdId);
        MortgageScenario scenario = useCase.findScenario(household, id);
        return MortgageRestMapper.toResponse(scenario, useCase.replayScenario(household, id));
    }

    @PutMapping("/scenarios/{id}")
    public ScenarioResponseDto updateScenario(
            @RequestHeader("X-Household-Id") UUID householdId,
            @PathVariable UUID id,
            @Valid @RequestBody ScenarioRequestDto request) {

        HouseholdId household = new HouseholdId(householdId);
        SimulationRequest simulation = MortgageRestMapper.toDomain(request.simulation());
        MortgageScenario scenario =
                useCase.updateScenario(household, id, request.name(), simulation);

        return MortgageRestMapper.toResponse(scenario, useCase.simulate(household, simulation));
    }

    @DeleteMapping("/scenarios/{id}")
    public ResponseEntity<Void> deleteScenario(
            @RequestHeader("X-Household-Id") UUID householdId,
            @PathVariable UUID id) {

        useCase.deleteScenario(new HouseholdId(householdId), id);
        return ResponseEntity.noContent().build();
    }
}
