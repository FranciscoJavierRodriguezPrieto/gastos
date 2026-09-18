package com.gastos.expenses.infrastructure.rest;

import com.gastos.expenses.application.ManageExpensesUseCase;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseRequest;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseResponse;
import com.gastos.expenses.infrastructure.rest.dto.MonthlySummaryResponse;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador REST del contexto de gastos.
 *
 * <p>El controlador no contiene logica: valida el contrato, delega en el caso de uso y
 * traduce el resultado. Si alguna vez aparece un {@code if} de negocio aqui, esta en el
 * sitio equivocado.</p>
 *
 * <p><strong>Provisional:</strong> la identidad llega por las cabeceras
 * {@code X-Household-Id} y {@code X-User-Id}. Es un andamio hasta
 * {@code feature/security-jwt-passkeys}, donde el hogar y el usuario se leeran del
 * token y estas cabeceras dejaran de existir. Hasta entonces la API no debe exponerse
 * fuera de la red local: cualquiera que invente una cabecera es cualquier hogar.</p>
 */
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ManageExpensesUseCase useCase;

    public ExpenseController(ManageExpensesUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> register(
            @RequestHeader("X-Household-Id") UUID householdId,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody ExpenseRequest request) {

        ExpenseResponse response = ExpenseRestMapper.toResponse(useCase.register(
                ExpenseRestMapper.toCommand(new HouseholdId(householdId), new UserId(userId), request)));

        return ResponseEntity.created(URI.create("/api/v1/expenses/" + response.id())).body(response);
    }

    @GetMapping
    public List<ExpenseResponse> listByMonth(
            @RequestHeader("X-Household-Id") UUID householdId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        return ExpenseRestMapper.toResponses(
                useCase.listByMonth(new HouseholdId(householdId), month));
    }

    @GetMapping("/summary")
    public MonthlySummaryResponse summary(
            @RequestHeader("X-Household-Id") UUID householdId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        return ExpenseRestMapper.toSummary(useCase.summarize(new HouseholdId(householdId), month));
    }

    @GetMapping("/{id}")
    public ExpenseResponse findById(
            @RequestHeader("X-Household-Id") UUID householdId,
            @PathVariable UUID id) {

        return ExpenseRestMapper.toResponse(
                useCase.findById(new HouseholdId(householdId), new ExpenseId(id)));
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(
            @RequestHeader("X-Household-Id") UUID householdId,
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request) {

        return ExpenseRestMapper.toResponse(useCase.update(
                new HouseholdId(householdId), new ExpenseId(id),
                ExpenseRestMapper.toUpdateCommand(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Household-Id") UUID householdId,
            @PathVariable UUID id) {

        useCase.delete(new HouseholdId(householdId), new ExpenseId(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * Catalogo de categorias y periodicidades.
     *
     * <p>Lo publica el servidor para que el frontend no tenga que mantener una copia de
     * los valores admitidos y desincronizarse en la siguiente version.</p>
     */
    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        List<Map<String, Object>> categories = Arrays.stream(ExpenseCategory.values())
                .map(category -> Map.<String, Object>of(
                        "value", category.name(),
                        "label", category.displayName(),
                        "recurringCommitment", category.isRecurringCommitment()))
                .toList();

        List<Map<String, Object>> recurrences = Arrays.stream(Recurrence.values())
                .map(recurrence -> Map.<String, Object>of(
                        "value", recurrence.name(),
                        "recurring", recurrence.isRecurring()))
                .toList();

        return Map.of("categories", categories, "recurrences", recurrences);
    }
}
