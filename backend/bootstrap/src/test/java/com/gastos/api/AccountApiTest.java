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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("API de cuentas")
class AccountApiTest extends ApiTestSupport {

    private static String account(String alias, String type, String ownership, String holder,
                                  String balance) {
        return """
                {"alias":"%s","bankName":"Banco Ejemplo","type":"%s",\
                "ownership":"%s","holders":["%s"],"initialBalance":%s}"""
                .formatted(alias, type, ownership, holder, balance);
    }

    @Test
    @DisplayName("alta de cuenta: la respuesta no filtra el hogar al que pertenece")
    void openAccountDoesNotLeakHousehold() throws Exception {
        String token = tokenForNewHousehold();

        String response = mockMvc.perform(post("/api/v1/accounts")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Cuenta nomina", "CORRIENTE", "INDIVIDUAL",
                                UUID.randomUUID().toString(), "2210.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("Cuenta nomina"))
                .andExpect(jsonPath("$.balance").value(2210.00))
                .andReturn().getResponse().getContentAsString();

        // La respuesta no menciona el hogar por ningun lado.
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain("householdId");
    }

    @Test
    @DisplayName("una cuenta conjunta con un solo titular se rechaza con 422")
    void jointAccountNeedsTwoHolders() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .header(AUTHORIZATION, tokenForNewHousehold())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Cuenta conjunta", "CORRIENTE", "CONJUNTA",
                                UUID.randomUUID().toString(), "0.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("dos titulares")));
    }

    @Test
    @DisplayName("un cargo que deja la cuenta en descubierto se rechaza con 422")
    void overdraftIsRejected() throws Exception {
        String token = tokenForNewHousehold();

        String created = mockMvc.perform(post("/api/v1/accounts")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Cuenta nomina", "CORRIENTE", "INDIVIDUAL",
                                UUID.randomUUID().toString(), "100.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/accounts/{id}/debit", id)
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":150.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("descubierto")));
    }

    @Test
    @DisplayName("una cuenta de otro hogar responde 404")
    void otherHouseholdGetsNotFound() throws Exception {
        String token = tokenForNewHousehold();

        String created = mockMvc.perform(post("/api/v1/accounts")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Ahorro", "AHORRO", "INDIVIDUAL",
                                UUID.randomUUID().toString(), "500.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/accounts/{id}", id)
                        .header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ingreso, conciliacion y saldo total del hogar")
    void balanceOperationsAndTotal() throws Exception {
        String token = tokenForNewHousehold();

        String created = mockMvc.perform(post("/api/v1/accounts")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(account("Ahorro", "AHORRO", "INDIVIDUAL",
                                UUID.randomUUID().toString(), "1000.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/api/v1/accounts/{id}/credit", id)
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1250.00));

        mockMvc.perform(put("/api/v1/accounts/{id}/balance", id)
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1200.75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1200.75));

        mockMvc.perform(get("/api/v1/accounts/total-balance").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(1200.75));
    }
}
