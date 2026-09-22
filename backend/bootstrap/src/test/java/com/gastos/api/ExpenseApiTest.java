package com.gastos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Pruebas de la API de gastos de extremo a extremo: HTTP, validacion, mapeo, caso de
 * uso, dominio y repositorio.
 *
 * <p>El limitador de peticiones se desactiva aqui para que los tests no dependan del
 * numero de llamadas; tiene su propia prueba en {@code RateLimitApiTest}.</p>
 */
@DisplayName("API de gastos")
class ExpenseApiTest extends ApiTestSupport {

    private String body(String description, String amount, String category, String recurrence,
                        String date) {
        return """
                {"description":"%s","amount":%s,"category":"%s","recurrence":"%s","incurredOn":"%s"}"""
                .formatted(description, amount, category, recurrence, date);
    }

    /** Registra un gasto con la identidad indicada y devuelve su identificador. */
    private String registerExpense(String bearerToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/expenses")
                        .header(AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Compra semanal", "180.50", "ALIMENTACION", "PUNTUAL",
                                "2026-03-04")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    @DisplayName("alta, consulta, modificacion y borrado de un gasto")
    void fullCrudCycle() throws Exception {
        String id = registerExpense(TOKEN);

        mockMvc.perform(get("/api/v1/expenses/{id}", id).header(AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Compra semanal"))
                .andExpect(jsonPath("$.amount").value(180.50))
                .andExpect(jsonPath("$.categoryLabel").value("Alimentacion"));

        mockMvc.perform(put("/api/v1/expenses/{id}", id)
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Compra corregida", "142.30", "ALIMENTACION", "PUNTUAL",
                                "2026-03-05")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(142.30));

        mockMvc.perform(delete("/api/v1/expenses/{id}", id).header(AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/expenses/{id}", id).header(AUTHORIZATION, TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el alta devuelve 201 con la cabecera Location")
    void createReturnsLocation() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Gasolina", "60.00", "COCHE", "PUNTUAL", "2026-03-02")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("un gasto de otro hogar responde 404, no 403")
    void otherHouseholdGetsNotFound() throws Exception {
        String id = registerExpense(TOKEN);

        // Devolver 403 confirmaria que el identificador existe y permitiria enumerarlos.
        mockMvc.perform(get("/api/v1/expenses/{id}", id).header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("un importe negativo se rechaza con 400 y el detalle del campo")
    void negativeAmountIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Devolucion", "-10.00", "OTROS", "PUNTUAL", "2026-03-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    @DisplayName("una categoria inexistente se rechaza con 422 y los valores admitidos")
    void unknownCategoryIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Algo", "10.00", "CRIPTOMONEDAS", "PUNTUAL", "2026-03-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ALIMENTACION")));
    }

    @Test
    @DisplayName("sin token la peticion es 401, no 400")
    void missingTokenIsRejected() throws Exception {
        // La identidad ya no llega en una cabecera que el cliente pueda inventarse: sin
        // token valido la peticion no pasa de la cadena de seguridad.
        mockMvc.perform(get("/api/v1/expenses").param("month", "2026-03"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un campo desconocido en el cuerpo se rechaza en vez de ignorarse")
    void unknownFieldIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Compra","amount":10.00,"category":"OCIO",\
                                "recurrence":"PUNTUAL","incurredOn":"2026-03-01",\
                                "householdId":"00000000-0000-0000-0000-000000000000"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el resumen mensual agrega total, compromisos y desglose")
    void monthlySummaryAggregates() throws Exception {
        String token = tokenForNewHousehold();

        mockMvc.perform(post("/api/v1/expenses")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Prestamo coche", "225.00", "COCHE", "MENSUAL", "2026-03-01")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/expenses")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Concierto", "75.00", "OCIO", "PUNTUAL", "2026-03-20")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/expenses/summary")
                        .header(AUTHORIZATION, token)
                        .param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(300.00))
                .andExpect(jsonPath("$.monthlyCommitments").value(225.00))
                .andExpect(jsonPath("$.byCategory[0].category").value("COCHE"));
    }

    @Test
    @DisplayName("el catalogo publica categorias y periodicidades para el frontend")
    void catalogIsPublished() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/catalog").header(AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.recurrences").isArray());
    }
}
