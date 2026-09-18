package com.gastos.expenses.infrastructure.rest;

import com.gastos.expenses.application.ManageExpensesUseCase;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.ExpenseId;
import com.gastos.expenses.domain.model.Recurrence;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseRequest;
import com.gastos.expenses.infrastructure.rest.dto.ExpenseResponse;
import com.gastos.expenses.infrastructure.rest.dto.MonthlySummaryResponse;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.web.CurrentUser;
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
 * <p>La identidad llega ya verificada en {@code AuthenticatedUser}, extraida del token
 * de acceso. El controlador no lee cabeceras ni claims: recibe quien pregunta y no tiene
 * forma de saltarse la comprobacion.</p>
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
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody ExpenseRequest request) {

        ExpenseResponse response = ExpenseRestMapper.toResponse(useCase.register(
                ExpenseRestMapper.toCommand(user.householdId(), user.userId(), request)));

        return ResponseEntity.created(URI.create("/api/v1/expenses/" + response.id())).body(response);
    }

    @GetMapping
    public List<ExpenseResponse> listByMonth(
            @CurrentUser AuthenticatedUser user,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        return ExpenseRestMapper.toResponses(
                useCase.listByMonth(user.householdId(), month));
    }

    @GetMapping("/summary")
    public MonthlySummaryResponse summary(
            @CurrentUser AuthenticatedUser user,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        return ExpenseRestMapper.toSummary(useCase.summarize(user.householdId(), month));
    }

    @GetMapping("/{id}")
    public ExpenseResponse findById(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id) {

        return ExpenseRestMapper.toResponse(
                useCase.findById(user.householdId(), new ExpenseId(id)));
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request) {

        return ExpenseRestMapper.toResponse(useCase.update(
                user.householdId(), new ExpenseId(id),
                ExpenseRestMapper.toUpdateCommand(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id) {

        useCase.delete(user.householdId(), new ExpenseId(id));
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
