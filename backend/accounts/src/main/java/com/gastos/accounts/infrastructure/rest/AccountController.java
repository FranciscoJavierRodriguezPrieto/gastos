package com.gastos.accounts.infrastructure.rest;

import com.gastos.accounts.application.ManageAccountsUseCase;
import com.gastos.accounts.domain.model.AccountId;
import com.gastos.accounts.infrastructure.rest.dto.AccountRequest;
import com.gastos.accounts.infrastructure.rest.dto.AccountResponse;
import com.gastos.accounts.infrastructure.rest.dto.BalanceOperationRequest;
import com.gastos.accounts.infrastructure.rest.dto.RenameAccountRequest;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.web.CurrentUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
 * Adaptador REST del contexto de cuentas.
 *
 * <p>Las operaciones sobre el saldo son verbos de negocio con recurso propio
 * ({@code /credit}, {@code /debit}, {@code /reconcile}) y no un PUT generico sobre la
 * cuenta: cada una tiene su DTO, su validacion y su significado contable.</p>
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final ManageAccountsUseCase useCase;

    public AccountController(ManageAccountsUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody AccountRequest request) {

        AccountResponse response = AccountRestMapper.toResponse(useCase.open(
                AccountRestMapper.toCommand(user.householdId(), request)));

        return ResponseEntity.created(URI.create("/api/v1/accounts/" + response.id())).body(response);
    }

    @GetMapping
    public List<AccountResponse> list(@CurrentUser AuthenticatedUser user) {
        return AccountRestMapper.toResponses(useCase.listByHousehold(user.householdId()));
    }

    /** Patrimonio agregado del hogar: cifra de cabecera del resumen. */
    @GetMapping("/total-balance")
    public Map<String, BigDecimal> totalBalance(@CurrentUser AuthenticatedUser user) {
        return Map.of("totalBalance", useCase.totalBalance(user.householdId()).amount());
    }

    @GetMapping("/{id}")
    public AccountResponse findById(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id) {

        return AccountRestMapper.toResponse(
                useCase.findById(user.householdId(), new AccountId(id)));
    }

    @PutMapping("/{id}/alias")
    public AccountResponse rename(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RenameAccountRequest request) {

        return AccountRestMapper.toResponse(
                useCase.rename(user.householdId(), new AccountId(id), request.alias()));
    }

    @PostMapping("/{id}/credit")
    public AccountResponse credit(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody BalanceOperationRequest request) {

        return AccountRestMapper.toResponse(useCase.credit(
                user.householdId(), new AccountId(id), Money.euros(request.amount())));
    }

    @PostMapping("/{id}/debit")
    public AccountResponse debit(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody BalanceOperationRequest request) {

        return AccountRestMapper.toResponse(useCase.debit(
                user.householdId(), new AccountId(id), Money.euros(request.amount())));
    }

    @PutMapping("/{id}/balance")
    public AccountResponse reconcile(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody BalanceOperationRequest request) {

        return AccountRestMapper.toResponse(useCase.reconcile(
                user.householdId(), new AccountId(id), Money.euros(request.amount())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID id) {

        useCase.close(user.householdId(), new AccountId(id));
        return ResponseEntity.noContent().build();
    }
}
