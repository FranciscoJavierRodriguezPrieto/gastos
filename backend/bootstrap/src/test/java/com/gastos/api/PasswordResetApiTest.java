package com.gastos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gastos.iam.application.port.ResetTokenService;
import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.port.EmailSender;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Flujo completo de restablecimiento de contrasena.
 *
 * <p>El correo se captura con un doble en vez de montar un servidor SMTP: lo que hay que
 * comprobar es que se envia al destinatario correcto y que el token del enlace funciona
 * una sola vez, no que Jakarta Mail sepa hablar SMTP.</p>
 *
 * <p>Base de datos propia y tests ordenados porque comparten el estado de la instalacion:
 * el alta inicial solo funciona una vez.</p>
 */
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@Import(PasswordResetApiTest.CorreoDePrueba.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API de restablecimiento de contrasena")
class PasswordResetApiTest extends ApiTestSupport {

    private static final String EMAIL = "titular@ejemplo.es";
    private static final String PASSWORD_INICIAL = "una-contrasena-larga-y-decente";
    private static final String PASSWORD_NUEVA = "otra-contrasena-mas-larga-aun";

    private static String tokenDelEnlace;

    @Autowired
    private Buzon buzon;

    @Autowired
    private ResetTokenService resetTokenService;

    /** Doble del emisor de correo que guarda lo enviado, para poder inspeccionarlo. */
    record Mensaje(String destinatario, String asunto, String cuerpo) { }

    static class Buzon {
        private final List<Mensaje> mensajes = new ArrayList<>();

        void guardar(Mensaje mensaje) {
            mensajes.add(mensaje);
        }

        List<Mensaje> mensajes() {
            return mensajes;
        }

        Mensaje ultimo() {
            return mensajes.get(mensajes.size() - 1);
        }
    }

    @TestConfiguration
    static class CorreoDePrueba {

        @Bean
        Buzon buzon() {
            return new Buzon();
        }

        @Bean
        @Primary
        EmailSender emailSenderDePrueba(Buzon buzon) {
            return (Email destinatario, String asunto, String cuerpo) ->
                    buzon.guardar(new Mensaje(destinatario.value(), asunto, cuerpo));
        }
    }

    private void pedirEnlace(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(email)))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(1)
    @DisplayName("se crea el hogar de partida")
    void registrar() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"Casa","email":"%s","displayName":"Titular",\
                                "password":"%s","monthlyNetIncome":2200.00}"""
                                .formatted(EMAIL, PASSWORD_INICIAL)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(2)
    @DisplayName("un correo desconocido responde igual que uno real y no envia nada")
    void unknownEmailIsIndistinguishable() throws Exception {
        int antes = buzon.mensajes().size();

        pedirEnlace("nadie@ejemplo.es");
        // Un correo con formato invalido tampoco puede distinguirse.
        pedirEnlace("esto-no-es-un-correo");

        assertThat(buzon.mensajes()).hasSize(antes);
    }

    @Test
    @Order(3)
    @DisplayName("un correo registrado recibe el enlace con su token")
    void registeredEmailGetsTheLink() throws Exception {
        pedirEnlace(EMAIL);

        Mensaje mensaje = buzon.ultimo();
        assertThat(mensaje.destinatario()).isEqualTo(EMAIL);
        assertThat(mensaje.asunto()).contains("Restablecer");
        assertThat(mensaje.cuerpo()).contains("/#/restablecer?token=");
        // El cuerpo no contiene la contrasena ni ningun hash.
        assertThat(mensaje.cuerpo()).doesNotContain(PASSWORD_INICIAL).doesNotContain("$2");

        tokenDelEnlace = mensaje.cuerpo().split("token=")[1].split("\\s")[0];
        assertThat(tokenDelEnlace).isNotBlank();
    }

    @Test
    @Order(4)
    @DisplayName("pedir el enlace otra vez invalida el anterior")
    void askingAgainInvalidatesThePreviousLink() throws Exception {
        String anterior = tokenDelEnlace;

        pedirEnlace(EMAIL);
        String nuevo = buzon.ultimo().cuerpo().split("token=")[1].split("\\s")[0];
        assertThat(nuevo).isNotEqualTo(anterior);

        // El viejo ya no sirve: no puede haber dos puertas abiertas.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}"""
                                .formatted(anterior, PASSWORD_NUEVA)))
                .andExpect(status().isBadRequest());

        tokenDelEnlace = nuevo;
    }

    @Test
    @Order(5)
    @DisplayName("una contrasena corta se rechaza y el enlace sigue sirviendo")
    void shortPasswordIsRejectedWithoutBurningTheToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"corta"}""".formatted(tokenDelEnlace)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @Order(6)
    @DisplayName("el enlace restablece la contrasena y deja la sesion iniciada")
    void linkResetsThePassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}"""
                                .formatted(tokenDelEnlace, PASSWORD_NUEVA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        // La nueva vale y la vieja ya no.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD_NUEVA)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD_INICIAL)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    @DisplayName("el enlace no se puede usar dos veces")
    void linkIsSingleUse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}"""
                                .formatted(tokenDelEnlace, "y-otra-contrasena-mas")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("un token inventado se rechaza igual que uno caducado")
    void madeUpTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"me-lo-acabo-de-inventar","newPassword":"%s"}"""
                                .formatted(PASSWORD_NUEVA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(9)
    @DisplayName("cambiar la contrasena exige la actual")
    void changingPasswordRequiresTheCurrentOne() throws Exception {
        String sesion = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD_NUEVA)))
                .andReturn().getResponse().getContentAsString();
        String acceso = "Bearer " + objectMapper.readTree(sesion).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/password")
                        .header(AUTHORIZATION, acceso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"la-que-no-es","newPassword":"una-nueva-larguisima"}"""))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/password")
                        .header(AUTHORIZATION, acceso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"una-nueva-larguisima"}"""
                                .formatted(PASSWORD_NUEVA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"una-nueva-larguisima"}""".formatted(EMAIL)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(10)
    @DisplayName("restablecer y cambiar la contrasena tiran las sesiones anteriores")
    void resettingRevokesEveryOtherSession() throws Exception {
        String sesion = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"una-nueva-larguisima"}""".formatted(EMAIL)))
                .andReturn().getResponse().getContentAsString();
        String refrescoViejo = objectMapper.readTree(sesion).get("refreshToken").asText();

        pedirEnlace(EMAIL);
        String token = buzon.ultimo().cuerpo().split("token=")[1].split("\\s")[0];

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"contrasena-final-larga"}"""
                                .formatted(token)))
                .andExpect(status().isOk());

        // Quien recupera el acceso es porque lo habia perdido: fuera todas las sesiones.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refrescoViejo)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("el enlace apunta al frontend y lleva el token en el fragmento")
    void linkPointsToTheFrontendFragment() {
        String url = resetTokenService.resetUrl("TOKEN-DE-PRUEBA");

        // En el fragmento y no en la query: lo que va tras la almohadilla no se envia al
        // servidor ni acaba en los registros de acceso.
        assertThat(url).contains("/#/restablecer?token=TOKEN-DE-PRUEBA");
        assertThat(url).doesNotContain("?token=TOKEN-DE-PRUEBA&");
    }
}
