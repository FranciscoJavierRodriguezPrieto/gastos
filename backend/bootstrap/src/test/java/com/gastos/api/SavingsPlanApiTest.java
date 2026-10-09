package com.gastos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("API de la prevision de ahorro")
class SavingsPlanApiTest extends ApiTestSupport {

    private static final String PLAN = """
            {"startMonth":"2026-10",
             "people":[{"name":"Ana","initialBalance":0,"monthlyIncome":1600,"extraIncome":0,
                        "bonusAmount":1600,"bonusMonths":[6,12],"jointContribution":850,
                        "expenses":[{"name":"Coche","amount":225.03,"until":"2030-09"}]}],
             "joint":{"initialBalance":0,"extraIncome":425,"extraIncomeFrom":"2026-11",
                      "monthlyExpenses":1680,
                      "oneOffExpenses":[{"name":"Seguro","amount":318.06,"month":"2026-10"}]}}""";

    @Test
    @DisplayName("sin plan responde 204; guardado, se devuelve entero")
    void saveAndRead() throws Exception {
        mockMvc.perform(get("/api/v1/savings-plan").header(AUTHORIZATION, TOKEN))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/savings-plan").header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/savings-plan").header(AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.people[0].bonusMonths[1]").value(12))
                .andExpect(jsonPath("$.people[0].expenses[0].until").value("2030-09"))
                .andExpect(jsonPath("$.joint.oneOffExpenses[0].amount").value(318.06));
    }

    @Test
    @DisplayName("el plan de un hogar no lo ve otro")
    void isolatedByHousehold() throws Exception {
        mockMvc.perform(put("/api/v1/savings-plan").header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PLAN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/savings-plan").header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("rechaza meses mal formados y pagas en meses que no existen")
    void rejectsInvalidPlan() throws Exception {
        mockMvc.perform(put("/api/v1/savings-plan").header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLAN.replace("\"2026-10\",", "\"octubre\",")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/savings-plan").header(AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLAN.replace("[6,12]", "[6,13]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sin token, 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/savings-plan")).andExpect(status().isUnauthorized());
    }
}
