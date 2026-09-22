package com.gastos.expenses.infrastructure.rest;

import com.gastos.expenses.application.FixedExpenseCommand;
import com.gastos.expenses.application.ManageFixedExpensesUseCase;
import com.gastos.expenses.domain.model.ExpenseCategory;
import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.model.FixedExpenseId;
import com.gastos.expenses.infrastructure.rest.dto.FixedExpenseRequest;
import com.gastos.expenses.infrastructure.rest.dto.FixedExpenseResponse;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Money;
import com.gastos.shared.web.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
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
 * Adaptador REST de los gastos fijos.
 *
 * <p>Estas rutas manejan <strong>plantillas</strong>, no gastos. El gasto de un mes
 * concreto —el recibo de la luz de enero— se edita por {@code /api/v1/expenses}, como
 * cualquier otro, y eso no altera la plantilla ni los demas meses.</p>
 */
@RestController
@RequestMapping("/api/v1/fixed-expenses")
public class FixedExpenseController {

    private final ManageFixedExpensesUseCase useCase;

    public FixedExpenseController(ManageFixedExpensesUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<FixedExpenseResponse> list(@CurrentUser AuthenticatedUser user) {
        return useCase.list(user.householdId()).stream()
                .map(FixedExpenseController::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public FixedExpenseResponse findById(@CurrentUser AuthenticatedUser user,
                                         @PathVariable UUID id) {
        return toResponse(useCase.findById(user.householdId(), new FixedExpenseId(id)));
    }

    @PostMapping
    public ResponseEntity<FixedExpenseResponse> create(@CurrentUser AuthenticatedUser user,
                                                       @Valid @RequestBody FixedExpenseRequest request) {
        FixedExpenseResponse response = toResponse(useCase.create(
                user.householdId(), user.userId(), toCommand(request)));

        return ResponseEntity.created(URI.create("/api/v1/fixed-expenses/" + response.id()))
                .body(response);
    }

    /**
     * Cambia la plantilla.
     *
     * <p><strong>Solo afecta a los meses que aun no se han generado.</strong> El mes en
     * curso, si ya estaba generado, conserva su importe: para cambiarlo se edita ese
     * gasto. Es lo que permite que subir el alquiler no reescriba lo ya pagado.</p>
     */
    @PutMapping("/{id}")
    public FixedExpenseResponse update(@CurrentUser AuthenticatedUser user,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody FixedExpenseRequest request) {
        return toResponse(useCase.update(user.householdId(), new FixedExpenseId(id),
                toCommand(request)));
    }

    /** Deja de generar a partir del mes que viene. Lo ya generado se queda. */
    @PostMapping("/{id}/discontinue")
    public FixedExpenseResponse discontinue(@CurrentUser AuthenticatedUser user,
                                            @PathVariable UUID id) {
        return toResponse(useCase.discontinue(user.householdId(), new FixedExpenseId(id)));
    }

    @PostMapping("/{id}/reactivate")
    public FixedExpenseResponse reactivate(@CurrentUser AuthenticatedUser user,
                                           @PathVariable UUID id) {
        return toResponse(useCase.reactivate(user.householdId(), new FixedExpenseId(id)));
    }

    /**
     * Borra la plantilla del todo.
     *
     * <p>Los gastos que ya genero <strong>se conservan</strong>: son dinero gastado de
     * verdad. Para dejar de generar sin tocar nada mas, {@code /discontinue}.</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser user,
                                       @PathVariable UUID id) {
        useCase.delete(user.householdId(), new FixedExpenseId(id));
        return ResponseEntity.noContent().build();
    }

    private static FixedExpenseCommand toCommand(FixedExpenseRequest request) {
        return new FixedExpenseCommand(
                request.description(),
                Money.euros(request.amount()),
                parseCategory(request.category()),
                request.dayOfMonth(),
                request.accountId(),
                parseMonth(request.startMonth()));
    }

    private static FixedExpenseResponse toResponse(FixedExpense fijo) {
        return new FixedExpenseResponse(
                fijo.id().value(),
                fijo.description(),
                fijo.amount().amount(),
                fijo.category().name(),
                fijo.category().displayName(),
                fijo.dayOfMonth(),
                fijo.accountId(),
                fijo.startMonth().toString(),
                fijo.endMonth() == null ? null : fijo.endMonth().toString(),
                fijo.isActive());
    }

    private static ExpenseCategory parseCategory(String value) {
        try {
            return ExpenseCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Categoria no valida", e);
        }
    }

    /** Null significa "desde el mes en curso"; lo resuelve el caso de uso, que tiene reloj. */
    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new DomainException("El mes de inicio tiene formato yyyy-MM", e);
        }
    }
}
