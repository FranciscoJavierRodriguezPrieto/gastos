package com.gastos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Comprueba las medidas transversales de la API que responden al OWASP API Security
 * Top 10 (edicion 2023): limitacion de peticiones, cabeceras de seguridad y respuestas
 * de error que no filtran nada interno.
 */
@TestPropertySource(properties = {
        "gastos.rate-limit.enabled=true",
        "gastos.rate-limit.max-requests=3",
        "gastos.rate-limit.window-seconds=60"})
@DisplayName("Bastionado de la API")
class ApiHardeningTest extends ApiTestSupport {

    private static final String SIMULATION = """
            {"propertyPrice":200000,"availableSavings":70000,"targetReserve":6000,\
            "annualNominalRate":3.00,"termYears":30,"netMonthlyIncome":4000,\
            "otherMonthlyDebts":225,"applicantAge":32,"firstHome":true}""";

    @Test
    @DisplayName("API4: superar el limite de peticiones devuelve 429 con Retry-After")
    void rateLimitReturns429() throws Exception {
        String token = tokenForNewHousehold();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/mortgage/simulations")
                            .header(AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIMULATION))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/mortgage/simulations")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIMULATION))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("API8: toda respuesta lleva las cabeceras de seguridad")
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/catalog")
                        .header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("API8: una ruta inexistente devuelve 404 sin filtrar detalles internos")
    void unknownRouteLeaksNothing() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe")
                        .header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("API9: el actuator solo expone health, y sin detalle")
    void actuatorSurfaceIsMinimal() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());

        // Con token valido: no es que esten protegidos, es que no existen.
        mockMvc.perform(get("/actuator/env").header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound());
    }
}
