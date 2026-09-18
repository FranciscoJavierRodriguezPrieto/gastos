package com.gastos.accounts.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.accounts.application.OpenAccountCommand;
import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountType;
import com.gastos.accounts.domain.model.Ownership;
import com.gastos.accounts.infrastructure.rest.dto.AccountRequest;
import com.gastos.accounts.infrastructure.rest.dto.AccountResponse;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Mapper REST de cuentas")
class AccountRestMapperTest {

    private static final HouseholdId HOUSEHOLD = HouseholdId.newId();
    private static final UserId HOLDER = UserId.newId();

    @Test
    @DisplayName("convierte el DTO de entrada a tipos de dominio")
    void mapsRequestToCommand() {
        AccountRequest request = new AccountRequest("Cuenta nomina", "Banco Ejemplo",
                "corriente", "individual", Set.of(HOLDER.value()), new BigDecimal("2210.00"));

        OpenAccountCommand command = AccountRestMapper.toCommand(HOUSEHOLD, request);

        assertThat(command.type()).isEqualTo(AccountType.CORRIENTE);
        assertThat(command.ownership()).isEqualTo(Ownership.INDIVIDUAL);
        assertThat(command.initialBalance()).isEqualTo(Money.euros("2210.00"));
        assertThat(command.holders()).containsExactly(HOLDER);
    }

    @Test
    @DisplayName("la respuesta lleva solo los campos declarados, sin el hogar")
    void responseExposesOnlyDeclaredFields() {
        Account account = Account.open(HOUSEHOLD, "Cuenta nomina", "Banco Ejemplo",
                AccountType.CORRIENTE, Ownership.INDIVIDUAL, Set.of(HOLDER),
                Money.euros("2210.00"), Instant.parse("2026-03-01T10:00:00Z"));

        AccountResponse response = AccountRestMapper.toResponse(account);

        assertThat(response.alias()).isEqualTo("Cuenta nomina");
        assertThat(response.bankName()).isEqualTo("Banco Ejemplo");
        assertThat(response.balance()).isEqualByComparingTo("2210.00");
        assertThat(response.holders()).containsExactly(HOLDER.value());
        assertThat(response.balanceUpdatedAt()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
    }

    @Test
    @DisplayName("rechaza un tipo de cuenta desconocido indicando los valores admitidos")
    void rejectsUnknownType() {
        AccountRequest request = new AccountRequest("Cuenta", "Banco Ejemplo",
                "hucha", "individual", Set.of(HOLDER.value()), BigDecimal.ZERO);

        assertThatThrownBy(() -> AccountRestMapper.toCommand(HOUSEHOLD, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("CORRIENTE");
    }

    @Test
    @DisplayName("rechaza una titularidad desconocida indicando los valores admitidos")
    void rejectsUnknownOwnership() {
        AccountRequest request = new AccountRequest("Cuenta", "Banco Ejemplo",
                "corriente", "compartida", Set.of(HOLDER.value()), BigDecimal.ZERO);

        assertThatThrownBy(() -> AccountRestMapper.toCommand(HOUSEHOLD, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("CONJUNTA");
    }
}
