package com.gastos.accounts.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.accounts.application.OpenAccountCommand;
import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountType;
import com.gastos.accounts.domain.model.Iban;
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
    private static final String VALID_IBAN = "ES9121000418450200051332";

    @Test
    @DisplayName("convierte el DTO de entrada a tipos de dominio")
    void mapsRequestToCommand() {
        AccountRequest request = new AccountRequest("Cuenta nomina", "Banco Ejemplo", VALID_IBAN,
                "corriente", "individual", Set.of(HOLDER.value()), new BigDecimal("2210.00"));

        OpenAccountCommand command = AccountRestMapper.toCommand(HOUSEHOLD, request);

        assertThat(command.type()).isEqualTo(AccountType.CORRIENTE);
        assertThat(command.ownership()).isEqualTo(Ownership.INDIVIDUAL);
        assertThat(command.initialBalance()).isEqualTo(Money.euros("2210.00"));
        assertThat(command.holders()).containsExactly(HOLDER);
    }

    @Test
    @DisplayName("acepta el IBAN con espacios y lo normaliza")
    void normalizesIbanWithSpaces() {
        AccountRequest request = new AccountRequest("Ahorro", "Banco Ejemplo",
                "ES91 2100 0418 4502 0005 1332", "ahorro", "individual",
                Set.of(HOLDER.value()), BigDecimal.ZERO);

        assertThat(AccountRestMapper.toCommand(HOUSEHOLD, request).iban())
                .isEqualTo(new Iban(VALID_IBAN));
    }

    @Test
    @DisplayName("rechaza un IBAN con digito de control incorrecto")
    void rejectsInvalidIban() {
        AccountRequest request = new AccountRequest("Cuenta", "Banco Ejemplo",
                "ES9921000418450200051332", "corriente", "individual",
                Set.of(HOLDER.value()), BigDecimal.ZERO);

        assertThatThrownBy(() -> AccountRestMapper.toCommand(HOUSEHOLD, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("digito de control");
    }

    @Test
    @DisplayName("la respuesta nunca contiene el IBAN completo")
    void responseNeverExposesFullIban() {
        Account account = Account.open(HOUSEHOLD, "Cuenta nomina", "Banco Ejemplo",
                new Iban(VALID_IBAN), AccountType.CORRIENTE, Ownership.INDIVIDUAL,
                Set.of(HOLDER), Money.euros("2210.00"), Instant.parse("2026-03-01T10:00:00Z"));

        AccountResponse response = AccountRestMapper.toResponse(account);

        assertThat(response.maskedIban()).doesNotContain(VALID_IBAN);
        assertThat(response.maskedIban()).startsWith("ES").endsWith("1332");
        assertThat(response.balance()).isEqualByComparingTo("2210.00");
        assertThat(response.holders()).containsExactly(HOLDER.value());
    }

    @Test
    @DisplayName("rechaza un tipo de cuenta desconocido indicando los valores admitidos")
    void rejectsUnknownType() {
        AccountRequest request = new AccountRequest("Cuenta", "Banco Ejemplo", VALID_IBAN,
                "hucha", "individual", Set.of(HOLDER.value()), BigDecimal.ZERO);

        assertThatThrownBy(() -> AccountRestMapper.toCommand(HOUSEHOLD, request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("CORRIENTE");
    }
}
