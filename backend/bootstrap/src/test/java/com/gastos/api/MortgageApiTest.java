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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("API de hipoteca")
class MortgageApiTest extends ApiTestSupport {

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
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyPayment").exists())
                // Vivienda habitual de menos de 250.000: ITP bonificado al 5,4%.
                .andExpect(jsonPath("$.upfrontCosts.total").value(18800.00))
                .andExpect(jsonPath("$.upfrontCosts.transferTaxRate").value(5.40))
                .andExpect(jsonPath("$.upfrontCosts.transferTaxBasis").value(
                        org.hamcrest.Matchers.containsString("vivienda habitual")))
                // El ahorro cubre de sobra la entrada minima, asi que el prestamo lo fija
                // el ahorro aportado y no el LTV maximo.
                .andExpect(jsonPath("$.financing.loanAmount").value(154800.00))
                .andExpect(jsonPath("$.financingDecision.appliedLoanToValue").value(80.00))
                .andExpect(jsonPath("$.viability.verdict").value("OPTIMA"));
    }

    @Test
    @DisplayName("el escenario del 10% de gastos no cubierto sale INVIABLE con su motivo")
    void insufficientSavingsIsReported() throws Exception {
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, TOKEN)
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
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(200_000, 70_000, 6_000, "3.00", 45, 4_000, 225, 32)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("termYears")));
    }

    @Test
    @DisplayName("sin token la simulacion responde 401")
    void simulationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con un token manipulado la simulacion responde 401")
    void tamperedTokenIsRejected() throws Exception {
        // Se altera el PRIMER caracter de la firma, no el ultimo.
        //
        // Una firma HS256 son 32 bytes = 256 bits, que en base64url ocupan 43 caracteres
        // = 258 bits: del ultimo caracter solo cuentan dos. Cambiarlo daba los mismos 32
        // bytes una de cada cuatro veces, el token seguia siendo valido y este test
        // —que existe para comprobar justo lo contrario— pasaba sin comprobar nada.
        int inicioDeLaFirma = TOKEN.lastIndexOf('.') + 1;
        char primero = TOKEN.charAt(inicioDeLaFirma);
        String tampered = TOKEN.substring(0, inicioDeLaFirma)
                + (primero == 'A' ? 'B' : 'A')
                + TOKEN.substring(inicioDeLaFirma + 1);

        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el modo MANUAL exige indicar el LTV y lo dice senalando el campo")
    void manualModeWithoutLoanToValueIsRejected() throws Exception {
        String payload = """
                {"propertyPrice":200000,"availableSavings":70000,"targetReserve":6000,\
                "annualNominalRate":3.00,"termYears":30,"netMonthlyIncome":4000,\
                "otherMonthlyDebts":225,"applicantAge":32,"firstHome":true,\
                "financingMode":"MANUAL"}""";

        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("manualLoanToValue")));
    }

    @Test
    @DisplayName("el modo MANUAL aplica el LTV escrito sin comprobar ningun requisito")
    void manualModeAppliesGivenLoanToValue() throws Exception {
        String payload = """
                {"propertyPrice":280000,"availableSavings":50000,"targetReserve":5000,\
                "annualNominalRate":3.00,"termYears":30,"netMonthlyIncome":5500,\
                "otherMonthlyDebts":225,"applicantAge":52,"firstHome":false,\
                "financingMode":"MANUAL","manualLoanToValue":100.00}""";

        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.financingDecision.mode").value("MANUAL"))
                .andExpect(jsonPath("$.financingDecision.appliedLoanToValue").value(100.00))
                .andExpect(jsonPath("$.financingDecision.appliedProgramId").doesNotExist());
    }

    @Test
    @DisplayName("guardar, releer y borrar un escenario")
    void scenarioLifecycle() throws Exception {
        String payload = """
                {"name":"Piso de 200k a 30 anos","simulation":%s}"""
                .formatted(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32));

        String created = mockMvc.perform(post("/api/v1/mortgage/scenarios")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Piso de 200k a 30 anos"))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        // Al releerlo se devuelve la entrada guardada y el resultado recalculado.
        mockMvc.perform(get("/api/v1/mortgage/scenarios/{id}", id).header(AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation.propertyPrice").value(200000.00))
                .andExpect(jsonPath("$.result.viability.verdict").value("OPTIMA"));

        mockMvc.perform(delete("/api/v1/mortgage/scenarios/{id}", id)
                        .header(AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("un escenario de otro hogar responde 404")
    void otherHouseholdCannotReadScenario() throws Exception {
        String payload = """
                {"name":"Escenario ajeno","simulation":%s}"""
                .formatted(simulation(200_000, 70_000, 6_000, "3.00", 30, 4_000, 225, 32));

        String created = mockMvc.perform(post("/api/v1/mortgage/scenarios")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/mortgage/scenarios/{id}", id)
                        .header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound());
    }
}
