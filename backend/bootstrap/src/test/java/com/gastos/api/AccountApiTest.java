package com.gastos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "gastos.rate-limit.enabled=false")
@DisplayName("API de cuentas")
class AccountApiTest {

    private static final String VALID_IBAN = "ES9121000418450200051332";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String account(String alias, String iban, String type, String ownership,
                                  String holder, String balance) {
        return """
                {"alias":"%s","bankName":"Banco Ejemplo","iban":"%s","type":"%s",\
                "ownership":"%s","holders":["%s"],"initialBalance":%s}"""
                .formatted(alias, iban, type, ownership, holder, balance);
    }

    @Test
    @DisplayName("alta de cuenta: la respuesta solo lleva el IBAN enmascarado")
    void openAccountMasksIban() throws Exception {
        String household = UUID.randomUUID().toString();

        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Household-Id", household)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Cuenta nomina", VALID_IBAN, "CORRIENTE", "INDIVIDUAL",
                                UUID.randomUUID().toString(), "2210.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedIban").value(
                        org.hamcrest.Matchers.endsWith("1332")))
                .andReturn().getResponse().getContentAsString();

        // Ningun campo de la respuesta contiene el IBAN completo.
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain(VALID_IBAN);
        org.assertj.core.api.Assertions.assertThat(
                objectMapper.readTree(response).get("balance").asDouble()).isEqualTo(2210.00);
    }

    @Test
    @DisplayName("un IBAN con digito de control incorrecto se rechaza con 422")
    void invalidIbanIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Household-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Cuenta", "ES9921000418450200051332", "CORRIENTE",
                                "INDIVIDUAL", UUID.randomUUID().toString(), "0.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("una cuenta conjunta con un solo titular se rechaza con 422")
    void jointAccountNeedsTwoHolders() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Household-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Cuenta conjunta", VALID_IBAN, "CORRIENTE", "CONJUNTA",
                                UUID.randomUUID().toString(), "0.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("dos titulares")));
    }

    @Test
    @DisplayName("un cargo que deja la cuenta en descubierto se rechaza con 422")
    void overdraftIsRejected() throws Exception {
        String household = UUID.randomUUID().toString();

        String created = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Household-Id", household)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Cuenta nomina", VALID_IBAN, "CORRIENTE", "INDIVIDUAL",
                                UUID.randomUUID().toString(), "100.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/accounts/{id}/debit", id)
                        .header("X-Household-Id", household)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":150.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("descubierto")));
    }

    @Test
    @DisplayName("ingreso, conciliacion y saldo total del hogar")
    void balanceOperationsAndTotal() throws Exception {
        String household = UUID.randomUUID().toString();

        String created = mockMvc.perform(post("/api/v1/accounts")
                        .header("X-Household-Id", household)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Ahorro", VALID_IBAN, "AHORRO", "INDIVIDUAL",
                                UUID.randomUUID().toString(), "1000.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/accounts/{id}/credit", id)
                        .header("X-Household-Id", household)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1250.00));

        mockMvc.perform(put("/api/v1/accounts/{id}/balance", id)
                        .header("X-Household-Id", household)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1200.75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1200.75));

        mockMvc.perform(get("/api/v1/accounts/total-balance").header("X-Household-Id", household))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(1200.75));
    }
}
