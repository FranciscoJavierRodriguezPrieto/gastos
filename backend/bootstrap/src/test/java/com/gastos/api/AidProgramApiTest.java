package com.gastos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/**
 * El catalogo de programas de ayuda es un CRUD normal: esa es justamente la idea, que
 * mantener la normativa al dia no requiera tocar codigo.
 */
@DisplayName("API del catalogo de programas de ayuda")
class AidProgramApiTest extends ApiTestSupport {

    private static String program(String name, String ltv, String maxPrice, String maxAge,
                                  boolean firstHome, boolean active) {
        return """
                {"name":"%s","maxLoanToValue":%s,"maxPropertyPrice":%s,"maxApplicantAge":%s,\
                "requiresFirstHome":%s,"active":%s,"sourceNote":"Pendiente de verificar"}"""
                .formatted(name, ltv, maxPrice, maxAge, firstHome, active);
    }

    private static String simulation(long price, long savings, long reserve, String rate, int years,
                                     long income, long debts, int age, String extra) {
        return """
                {"propertyPrice":%d,"availableSavings":%d,"targetReserve":%d,\
                "annualNominalRate":%s,"termYears":%d,"netMonthlyIncome":%d,\
                "otherMonthlyDebts":%d,"applicantAge":%d,"firstHome":true%s}"""
                .formatted(price, savings, reserve, rate, years, income, debts, age, extra);
    }

    @Test
    @DisplayName("alta, consulta, modificacion y borrado de un programa")
    void fullCrudCycle() throws Exception {
        String token = tokenForNewHousehold();

        String created = mockMvc.perform(post("/api/v1/mortgage/programs")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(program("Ayuda autonomica", "95.00", "390000", "35", true, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxLoanToValue").value(95.00))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(put("/api/v1/mortgage/programs/{id}", id)
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(program("Ayuda autonomica", "100.00", "420000", "40", true, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxLoanToValue").value(100.00))
                .andExpect(jsonPath("$.maxApplicantAge").value(40));

        mockMvc.perform(delete("/api/v1/mortgage/programs/{id}", id)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/mortgage/programs/{id}", id)
                        .header(AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("los limites se pueden dejar vacios para decir 'sin limite'")
    void optionalLimitsMayBeOmitted() throws Exception {
        String token = tokenForNewHousehold();

        mockMvc.perform(post("/api/v1/mortgage/programs")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(program("Sin topes", "90.00", "null", "null", false, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxPropertyPrice").doesNotExist())
                .andExpect(jsonPath("$.maxApplicantAge").doesNotExist());
    }

    @Test
    @DisplayName("un LTV por encima del 100% se rechaza con 400")
    void loanToValueAboveHundredIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/mortgage/programs")
                        .header(AUTHORIZATION, tokenForNewHousehold())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(program("Imposible", "120.00", "null", "null", false, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("el catalogo de partida es idempotente y trae las plantillas sin verificar apagadas")
    void referenceCatalogIsIdempotentAndCautious() throws Exception {
        String token = tokenForNewHousehold();

        mockMvc.perform(post("/api/v1/mortgage/programs/reference-catalog")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Pulsarlo dos veces no duplica nada.
        mockMvc.perform(post("/api/v1/mortgage/programs/reference-catalog")
                        .header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // La plantilla cuyas condiciones no estan confirmadas llega desactivada.
        mockMvc.perform(get("/api/v1/mortgage/programs").header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.active == false)].name")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("por verificar"))));
    }

    @Test
    @DisplayName("un programa de otro hogar responde 404")
    void otherHouseholdGetsNotFound() throws Exception {
        String token = tokenForNewHousehold();

        String created = mockMvc.perform(post("/api/v1/mortgage/programs")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(program("Ayuda privada", "95.00", "null", "null", false, true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/mortgage/programs/{id}", id)
                        .header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("dar de alta un programa cambia la simulacion sin desplegar nada")
    void newProgramChangesTheSimulationImmediately() throws Exception {
        String token = tokenForNewHousehold();
        String escenario = simulation(280_000, 50_000, 5_000, "3.00", 30, 4_500, 225, 32, "");

        // Sin programas: financiacion estandar del 80%.
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(escenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financingDecision.appliedLoanToValue").value(80.00));

        mockMvc.perform(post("/api/v1/mortgage/programs")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(program("Mi Primera Vivienda", "95.00", "390000", "35", true, true)))
                .andExpect(status().isCreated());

        // Mismo escenario, catalogo nuevo: el motor ya aplica el aval.
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(escenario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financingDecision.appliedLoanToValue").value(95.00))
                .andExpect(jsonPath("$.financingDecision.appliedProgramName")
                        .value("Mi Primera Vivienda"));
    }

    @Test
    @DisplayName("el modo PROGRAMA explica que requisito falta en vez de forzar el LTV")
    void chosenProgramReportsUnmetCriteria() throws Exception {
        String token = tokenForNewHousehold();

        String created = mockMvc.perform(post("/api/v1/mortgage/programs")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(program("Mi Primera Vivienda", "95.00", "390000", "35", true, true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String programId = objectMapper.readTree(created).get("id").asText();

        // 450.000 supera el precio maximo del programa.
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(450_000, 200_000, 0, "3.00", 30, 6_000, 0, 32,
                                ",\"financingMode\":\"PROGRAMA\",\"programId\":\"" + programId + "\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financingDecision.appliedLoanToValue").value(80.00))
                .andExpect(jsonPath("$.financingDecision.notes")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("No se cumplen los requisitos"))))
                .andExpect(jsonPath("$.financingDecision.evaluations[0].unmetCriteria")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("precio"))));
    }
}
