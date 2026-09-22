package com.gastos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Flujo completo de autenticacion contra la base de datos real.
 *
 * <p>Los tests van ordenados porque comparten el estado de la instalacion: el alta
 * inicial solo funciona una vez, que es justamente la regla que hay que demostrar. Se usa
 * su propio contexto ({@code @TestPropertySource}) para no arrastrar el hogar creado
 * aqui a las demas clases de prueba.</p>
 */
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API de autenticacion")
class AuthApiTest extends ApiTestSupport {

    private static final String PASSWORD = "una-contrasena-larga-y-decente";
    private static final String OWNER_EMAIL = "titular@ejemplo.es";

    private static String ownerAccessToken;
    private static String ownerRefreshToken;

    private String register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"Nuestra casa","email":"%s",\
                                "displayName":"Titular","password":"%s",\
                                "monthlyNetIncome":2200.00}"""
                                .formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @Order(1)
    @DisplayName("una instalacion nueva declara que necesita alta inicial")
    void newInstallationNeedsBootstrap() throws Exception {
        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsBootstrap").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("una contrasena corta se rechaza con 400 antes de crear nada")
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"Casa","email":"corta@ejemplo.es",\
                                "displayName":"Alguien","password":"corta","monthlyNetIncome":100}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(
                        org.hamcrest.Matchers.containsString("password")));

        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(jsonPath("$.needsBootstrap").value(true));
    }

    @Test
    @Order(3)
    @DisplayName("el alta inicial crea el hogar y devuelve ya la sesion iniciada")
    void registerCreatesHouseholdAndSession() throws Exception {
        String body = register(OWNER_EMAIL, PASSWORD);
        JsonNode json = objectMapper.readTree(body);

        ownerAccessToken = "Bearer " + json.get("accessToken").asText();
        ownerRefreshToken = json.get("refreshToken").asText();

        assertThat(json.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(json.get("user").get("role").asText()).isEqualTo("OWNER");
        // Ni rastro de la contrasena ni de su hash en la respuesta.
        assertThat(body).doesNotContain(PASSWORD).doesNotContain("$2");
    }

    @Test
    @Order(4)
    @DisplayName("el alta inicial no se puede repetir: no hay registro abierto")
    void registrationIsClosedAfterBootstrap() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"Otra casa","email":"intruso@ejemplo.es",\
                                "displayName":"Intruso","password":"otra-contrasena-larga",\
                                "monthlyNetIncome":1000}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ya tiene un hogar")));
    }

    @Test
    @Order(5)
    @DisplayName("el token da acceso a los datos del hogar")
    void tokenGrantsAccess() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(AUTHORIZATION, ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.role").value("OWNER"));

        mockMvc.perform(get("/api/v1/accounts").header(AUTHORIZATION, ownerAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    @DisplayName("una contrasena incorrecta y un correo inexistente dan la misma respuesta")
    void wrongCredentialsAreIndistinguishable() throws Exception {
        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"contrasena-equivocada"}"""
                                .formatted(OWNER_EMAIL)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nadie@ejemplo.es","password":"contrasena-equivocada"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Mismo cuerpo: el login no sirve para averiguar que correos estan dados de alta.
        assertThat(objectMapper.readTree(wrongPassword).get("message"))
                .isEqualTo(objectMapper.readTree(unknownEmail).get("message"));
    }

    @Test
    @Order(7)
    @DisplayName("el login devuelve una sesion nueva")
    void loginWorks() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(OWNER_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @Order(8)
    @DisplayName("el refresco rota el token y el anterior deja de valer")
    void refreshRotatesTheToken() throws Exception {
        String rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(ownerRefreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String newRefreshToken = objectMapper.readTree(rotated).get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(ownerRefreshToken);

        // Reutilizar el token ya canjeado: se rechaza y ademas cae toda la cadena.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(ownerRefreshToken)))
                .andExpect(status().isUnauthorized());

        // El nuevo tambien ha quedado revocado, porque la reutilizacion revoca la sesion.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(newRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    @DisplayName("cerrar sesion revoca el token, y repetirlo sigue saliendo bien")
    void logoutRevokesAndIsIdempotent() throws Exception {
        String session = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(OWNER_EMAIL, PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(session).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andExpect(status().isNoContent());

        // Repetirlo no falla: cerrar sesion no puede ser un oraculo de tokens validos.
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    @DisplayName("solo el titular puede generar el codigo de invitacion")
    void onlyOwnerCanInvite() throws Exception {
        mockMvc.perform(post("/api/v1/auth/invitations")
                        .header(AUTHORIZATION, tokenForMemberOf(ownerAccessToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/invitations")
                        .header(AUTHORIZATION, tokenForMemberOf(ownerAccessToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    @DisplayName("sin invitacion vigente, el titular ve que no hay ninguna")
    void noPendingInvitationAtFirst() throws Exception {
        mockMvc.perform(get("/api/v1/auth/invitations").header(AUTHORIZATION, ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.householdFull").value(false));
    }

    @Test
    @Order(12)
    @DisplayName("un codigo inventado no dice nada mas que que no vale")
    void unknownCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/invitations/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ZZZZ-ZZZZ-ZZZZ"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INVITATION"));
    }

    @Test
    @Order(13)
    @DisplayName("generar un codigo nuevo invalida el anterior")
    void issuingAgainRevokesThePrevious() throws Exception {
        String primero = codigoDeInvitacion();
        String segundo = codigoDeInvitacion();
        assertThat(primero).isNotEqualTo(segundo);

        mockMvc.perform(post("/api/v1/auth/invitations/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s"}""".formatted(primero)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INVITATION"));

        // El vigente si identifica el hogar y a quien invita, que es lo unico que revela.
        mockMvc.perform(post("/api/v1/auth/invitations/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s"}""".formatted(segundo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdName").value("Nuestra casa"))
                .andExpect(jsonPath("$.invitedBy").value("Titular"));
    }

    @Test
    @Order(14)
    @DisplayName("el titular puede revocar la invitacion antes de que se use")
    void ownerCanRevoke() throws Exception {
        String codigo = codigoDeInvitacion();

        mockMvc.perform(get("/api/v1/auth/invitations").header(AUTHORIZATION, ownerAccessToken))
                .andExpect(jsonPath("$.pending").value(true))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mockMvc.perform(delete("/api/v1/auth/invitations").header(AUTHORIZATION, ownerAccessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(altaCon(codigo, "revocada@ejemplo.es")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INVITATION"));
    }

    @Test
    @Order(15)
    @DisplayName("con el codigo, la pareja se da de alta ella misma y entra")
    void partnerJoinsWithTheCode() throws Exception {
        String codigo = codigoDeInvitacion();

        String respuesta = mockMvc.perform(post("/api/v1/auth/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(altaCon(codigo, "conviviente@ejemplo.es")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("MEMBER"))
                .andReturn().getResponse().getContentAsString();

        // La contrasena que ha elegido sirve para entrar: nadie mas la ha tocado.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"conviviente@ejemplo.es","password":"otra-contrasena-larga-valida"}"""))
                .andExpect(status().isOk());

        JsonNode sesion = objectMapper.readTree(respuesta);
        mockMvc.perform(get("/api/v1/auth/members")
                        .header(AUTHORIZATION, "Bearer " + sesion.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(16)
    @DisplayName("el codigo es de un solo uso")
    void theCodeWorksOnlyOnce() throws Exception {
        // El del test anterior ya se gasto; se recupera del hogar, que ahora esta lleno.
        mockMvc.perform(get("/api/v1/auth/invitations").header(AUTHORIZATION, ownerAccessToken))
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.householdFull").value(true));
    }

    @Test
    @Order(17)
    @DisplayName("con el hogar lleno no se emiten mas invitaciones")
    void fullHouseholdCannotInvite() throws Exception {
        mockMvc.perform(post("/api/v1/auth/invitations").header(AUTHORIZATION, ownerAccessToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("dos convivientes")));
    }

    /** Emite una invitacion y devuelve el codigo en claro, que solo llega aqui. */
    private String codigoDeInvitacion() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/invitations")
                        .header(AUTHORIZATION, ownerAccessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.joinUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("code").asText();
    }

    private String altaCon(String codigo, String correo) {
        return """
                {"code":"%s","email":"%s","displayName":"Conviviente",\
                "password":"otra-contrasena-larga-valida","monthlyNetIncome":1800.00}"""
                .formatted(codigo, correo);
    }
}
