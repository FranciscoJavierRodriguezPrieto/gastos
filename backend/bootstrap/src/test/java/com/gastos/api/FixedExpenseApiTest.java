package com.gastos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Gastos fijos de extremo a extremo: HTTP, expansion perezosa y persistencia.
 *
 * <p>Lo que se demuestra aqui es que <strong>abrir el mes basta</strong>: no hay tarea
 * programada ni nada que ejecutar a mano, y sin embargo el alquiler aparece.</p>
 */
@DisplayName("API de gastos fijos")
class FixedExpenseApiTest extends ApiTestSupport {

    private static final String MES = YearMonth.now().toString();
    private static final String MES_SIGUIENTE = YearMonth.now().plusMonths(1).toString();

    private String cuerpo(String descripcion, String importe, String categoria, int dia) {
        return """
                {"description":"%s","amount":%s,"category":"%s","dayOfMonth":%d}"""
                .formatted(descripcion, importe, categoria, dia);
    }

    /** Da de alta un gasto fijo y devuelve su identificador. */
    private String darDeAlta(String token, String descripcion, String importe, String categoria)
            throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/fixed-expenses")
                        .header(AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(descripcion, importe, categoria, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asText();
    }

    private JsonNode gastosDelMes(String token, String mes) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/expenses")
                        .header(AUTHORIZATION, token)
                        .param("month", mes))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("abrir el mes hace aparecer el gasto fijo, sin tocar nada mas")
    void openingTheMonthMaterialisesIt() throws Exception {
        darDeAlta(TOKEN, "Alquiler", "800.00", "VIVIENDA");

        JsonNode gastos = gastosDelMes(TOKEN, MES);

        assertThat(gastos).hasSize(1);
        assertThat(gastos.get(0).get("description").asText()).isEqualTo("Alquiler");
        assertThat(gastos.get(0).get("amount").asDouble()).isEqualTo(800.00);
        // Marcado como venido de plantilla, para que la pantalla pueda senalarlo.
        assertThat(gastos.get(0).get("fixedExpenseId").asText()).isNotBlank();
        // Mensual y de categoria que la banca computa: entra en el DTI.
        assertThat(gastos.get(0).get("recurrence").asText()).isEqualTo("MENSUAL");
        assertThat(gastos.get(0).get("stableCommitment").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("recargar el mes no duplica el gasto")
    void reloadingDoesNotDuplicate() throws Exception {
        darDeAlta(TOKEN, "Alquiler", "800.00", "VIVIENDA");

        gastosDelMes(TOKEN, MES);
        gastosDelMes(TOKEN, MES);
        JsonNode gastos = gastosDelMes(TOKEN, MES);

        assertThat(gastos).hasSize(1);
    }

    @Test
    @DisplayName("cuenta en el resumen del mes como cualquier otro gasto")
    void countsInTheMonthlySummary() throws Exception {
        darDeAlta(TOKEN, "Alquiler", "800.00", "VIVIENDA");

        mockMvc.perform(get("/api/v1/expenses/summary").header(AUTHORIZATION, TOKEN)
                        .param("month", MES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(800.00))
                .andExpect(jsonPath("$.monthlyCommitments").value(800.00));
    }

    /**
     * El caso que motivo la funcionalidad: la luz son 50 EUR de media, pero este mes
     * fueron 95. Se corrige este mes y el que viene sigue saliendo a 50.
     */
    @Test
    @DisplayName("corregir el importe de un mes no cambia el siguiente")
    void correctingOneMonthLeavesTheNextAlone() throws Exception {
        darDeAlta(TOKEN, "Luz", "50.00", "SUMINISTROS");

        JsonNode gastos = gastosDelMes(TOKEN, MES);
        String idGasto = gastos.get(0).get("id").asText();

        mockMvc.perform(put("/api/v1/expenses/" + idGasto)
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Luz","amount":95.00,"category":"SUMINISTROS",\
                                "recurrence":"MENSUAL","incurredOn":"%s-01"}""".formatted(MES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(95.00));

        assertThat(gastosDelMes(TOKEN, MES).get(0).get("amount").asDouble()).isEqualTo(95.00);
        assertThat(gastosDelMes(TOKEN, MES_SIGUIENTE).get(0).get("amount").asDouble())
                .isEqualTo(50.00);
    }

    @Test
    @DisplayName("subir el importe de la plantilla no reescribe el mes ya generado")
    void raisingTheTemplateDoesNotRewriteTheCurrentMonth() throws Exception {
        String idFijo = darDeAlta(TOKEN, "Alquiler", "800.00", "VIVIENDA");
        gastosDelMes(TOKEN, MES);

        mockMvc.perform(put("/api/v1/fixed-expenses/" + idFijo)
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Alquiler", "850.00", "VIVIENDA", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(850.00));

        assertThat(gastosDelMes(TOKEN, MES).get(0).get("amount").asDouble()).isEqualTo(800.00);
        assertThat(gastosDelMes(TOKEN, MES_SIGUIENTE).get(0).get("amount").asDouble())
                .isEqualTo(850.00);
    }

    @Test
    @DisplayName("borrar el gasto generado de un mes es definitivo")
    void deletingTheGeneratedExpenseSticks() throws Exception {
        darDeAlta(TOKEN, "Alquiler", "800.00", "VIVIENDA");
        String idGasto = gastosDelMes(TOKEN, MES).get(0).get("id").asText();

        mockMvc.perform(delete("/api/v1/expenses/" + idGasto).header(AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());

        // Volver a abrir el mes no lo resucita.
        assertThat(gastosDelMes(TOKEN, MES)).isEmpty();
    }

    @Test
    @DisplayName("dado de baja, deja de generar desde el mes que viene")
    void discontinuing() throws Exception {
        String idFijo = darDeAlta(TOKEN, "Gimnasio", "35.00", "OCIO");

        mockMvc.perform(post("/api/v1/fixed-expenses/" + idFijo + "/discontinue")
                        .header(AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(gastosDelMes(TOKEN, MES)).hasSize(1);
        assertThat(gastosDelMes(TOKEN, MES_SIGUIENTE)).isEmpty();
    }

    @Test
    @DisplayName("borrar la plantilla conserva lo que ya genero")
    void deletingTheTemplateKeepsHistory() throws Exception {
        String idFijo = darDeAlta(TOKEN, "Alquiler", "800.00", "VIVIENDA");
        gastosDelMes(TOKEN, MES);

        mockMvc.perform(delete("/api/v1/fixed-expenses/" + idFijo).header(AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/fixed-expenses").header(AUTHORIZATION, TOKEN))
                .andExpect(jsonPath("$.length()").value(0));

        // El gasto sigue ahi: es dinero que se pago de verdad, solo que ya sin plantilla.
        // El campo no viene a null sino que desaparece del JSON, porque la API omite los
        // nulos (jackson.default-property-inclusion: non_null).
        JsonNode gastos = gastosDelMes(TOKEN, MES);
        assertThat(gastos).hasSize(1);
        assertThat(gastos.get(0).has("fixedExpenseId")).isFalse();
    }

    @Test
    @DisplayName("un dia de cargo por encima de 28 se rechaza")
    void dayOfMonthIsValidated() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-expenses")
                        .header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Alquiler", "800.00", "VIVIENDA", 31)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("los gastos fijos de otro hogar no se ven ni se tocan")
    void isolatedByHousehold() throws Exception {
        String idFijo = darDeAlta(TOKEN, "Alquiler", "800.00", "VIVIENDA");
        String otroHogar = tokenForNewHousehold();

        mockMvc.perform(get("/api/v1/fixed-expenses").header(AUTHORIZATION, otroHogar))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/fixed-expenses/" + idFijo).header(AUTHORIZATION, otroHogar))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/fixed-expenses/" + idFijo).header(AUTHORIZATION, otroHogar))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sin token no se llega a los gastos fijos")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/fixed-expenses"))
                .andExpect(status().isUnauthorized());
    }
}
