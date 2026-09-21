package com.gastos.mortgage.infrastructure.rest;

import com.gastos.mortgage.application.ManageAidProgramsUseCase;
import com.gastos.mortgage.infrastructure.rest.dto.AidProgramRequest;
import com.gastos.mortgage.infrastructure.rest.dto.AidProgramResponse;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Percentage;
import com.gastos.shared.web.CurrentUser;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo de programas de ayuda, editable desde la aplicacion.
 *
 * <p>Que esto sea un CRUD normal y corriente es justamente el objetivo: las condiciones
 * de una convocatoria se corrigen desde una pantalla, no con un despliegue.</p>
 */
@RestController
@RequestMapping("/api/v1/mortgage/programs")
public class AidProgramController {

    private final ManageAidProgramsUseCase useCase;

    public AidProgramController(ManageAidProgramsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<AidProgramResponse> list(@CurrentUser AuthenticatedUser user) {
        return AidProgramRestMapper.toResponses(useCase.list(user.householdId()));
    }

    @GetMapping("/{id}")
    public AidProgramResponse findById(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id) {

        return AidProgramRestMapper.toResponse(useCase.findById(user.householdId(), id));
    }

    @PostMapping
    public ResponseEntity<AidProgramResponse> create(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody AidProgramRequest request) {

        AidProgramResponse response = AidProgramRestMapper.toResponse(useCase.create(
                user.householdId(),
                request.name(),
                Percentage.of(request.maxLoanToValue()),
                AidProgramRestMapper.toOptionalMoney(request.maxPropertyPrice()),
                request.maxApplicantAge(),
                request.requiresFirstHome(),
                request.requiresFamilyOrDefault(),
                request.active(),
                request.sourceNote()));

        return ResponseEntity.created(URI.create("/api/v1/mortgage/programs/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public AidProgramResponse update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody AidProgramRequest request) {

        return AidProgramRestMapper.toResponse(useCase.update(
                user.householdId(),
                id,
                request.name(),
                Percentage.of(request.maxLoanToValue()),
                AidProgramRestMapper.toOptionalMoney(request.maxPropertyPrice()),
                request.maxApplicantAge(),
                request.requiresFirstHome(),
                request.requiresFamilyOrDefault(),
                request.active(),
                request.sourceNote()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id) {

        useCase.delete(user.householdId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Instala el catalogo de partida para no empezar con la pantalla en blanco.
     *
     * <p>Es idempotente: pulsarlo dos veces no duplica nada. Las plantillas cuyas
     * condiciones no estan verificadas se instalan desactivadas.</p>
     */
    @PostMapping("/reference-catalog")
    public List<AidProgramResponse> installReferenceCatalog(
            @CurrentUser AuthenticatedUser user) {

        return AidProgramRestMapper.toResponses(
                useCase.installReferenceCatalog(user.householdId()));
    }
}
