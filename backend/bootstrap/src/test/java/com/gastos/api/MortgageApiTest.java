package com.gastos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@DisplayName("API de hipoteca")
class MortgageApiTest {

    private static final String HOUSEHOLD = UUID.randomUUID().toString();
    private static final String OTHER_HOUSEHOLD = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String simulation(long price, long savings, long reserve, String rate, int years,
                                     long income, long debts, int age) {
        return """
                {"propertyPrice":%d,"availableSavings":%d,"targetReserve":%d,\
                "annualNominalRate":%s,"termYears":%d,"netMonthlyIncome":%d,\
                "otherMonthlyDebts":%d,"applicantAge":%d,"firstHome":true}"""
                .formatted(price, savings, reserve, rate, years, income, debts, age);
    }

    @Test
    @DisplayName("la simulacion devuelve cuota, gastos, financiacion y veredicto")
    void simulationReturnsEveryBlock() throws Exception {
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPayment").exists())
                .andExpect(jsonPath("$.upfrontCosts.total").value(20000.00))
                .andExpect(jsonPath("$.financing.loanAmount").value(156000.00))
                .andExpect(jsonPath("$.viability.verdict").value("OPTIMA"))
                .andExpect(jsonPath("$.viability.warnings").isEmpty());
    }

    @Test
    @DisplayName("el escenario del 10% de gastos no cubierto sale INVIABLE con su motivo")
    void insufficientSavingsIsReported() throws Exception {
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(280_000, 10_000, 0, "1.78", 20, 3_400, 225, 32)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viability.verdict").value("INVIABLE"))
                .andExpect(jsonPath("$.viability.viable").value(false))
                .andExpect(jsonPath("$.viability.blockingReasons").isNotEmpty());
    }

    @Test
    @DisplayName("un plazo fuera de rango se rechaza con 400 antes de llegar al dominio")
    void outOfRangeTermIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(200_000, 70_000, 6_000, "3.00", 45, 4_000, 225, 32)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("termYears")));
    }

    @Test
    @DisplayName("guardar, releer y borrar un escenario")
    void scenarioLifecycle() throws Exception {
        String payload = """
                {"name":"Piso de 200k a 30 anos","simulation":%s}"""
                .formatted(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32));

        String created = mockMvc.perform(post("/api/v1/mortgage/scenarios")
                        .header("X-Household-Id", HOUSEHOLD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Piso de 200k a 30 anos"))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        // Al releerlo se devuelve la entrada guardada y el resultado recalculado.
        mockMvc.perform(get("/api/v1/mortgage/scenarios/{id}", id).header("X-Household-Id", HOUSEHOLD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation.propertyPrice").value(200000.00))
                .andExpect(jsonPath("$.result.viability.verdict").value("OPTIMA"));

        mockMvc.perform(delete("/api/v1/mortgage/scenarios/{id}", id)
                        .header("X-Household-Id", HOUSEHOLD))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("un escenario de otro hogar responde 404")
    void otherHouseholdCannotReadScenario() throws Exception {
        String payload = """
                {"name":"Escenario ajeno","simulation":%s}"""
                .formatted(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32));

        String created = mockMvc.perform(post("/api/v1/mortgage/scenarios")
                        .header("X-Household-Id", HOUSEHOLD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/mortgage/scenarios/{id}", id)
                        .header("X-Household-Id", OTHER_HOUSEHOLD))
                .andExpect(status().isNotFound());
    }
}
