package com.gastos.expenses.infrastructure.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gastos.expenses.domain.port.SavingsPlanRepository;
import com.gastos.expenses.infrastructure.rest.dto.SavingsPlanDto;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan de la prevision de ahorro del hogar.
 *
 * <p>Sin caso de uso en medio: no hay regla de negocio que aplicar, solo guardar y
 * devolver un documento que la validacion de {@link SavingsPlanDto} ya ha acotado. El
 * hogar sale del token, asi que no hay forma de leer ni escribir el plan de otro.</p>
 */
@RestController
@RequestMapping("/api/v1/savings-plan")
public class SavingsPlanController {

    private final SavingsPlanRepository repository;
    private final ObjectMapper objectMapper;

    public SavingsPlanController(SavingsPlanRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** {@code 204} mientras el hogar no haya guardado ningun plan. */
    @GetMapping
    public ResponseEntity<SavingsPlanDto> find(@CurrentUser AuthenticatedUser user) {
        return repository.find(user.householdId())
                .map(this::read)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public SavingsPlanDto save(@CurrentUser AuthenticatedUser user,
                               @Valid @RequestBody SavingsPlanDto plan) throws JsonProcessingException {
        repository.save(user.householdId(), objectMapper.writeValueAsString(plan));
        return plan;
    }

    private SavingsPlanDto read(String document) {
        try {
            return objectMapper.readValue(document, SavingsPlanDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("El plan guardado no se puede leer", e);
        }
    }
}
