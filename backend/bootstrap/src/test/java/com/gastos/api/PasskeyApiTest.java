package com.gastos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Ceremonia WebAuthn completa contra un autenticador emulado.
 *
 * <p>Este test no usa un doble que diga que si: el emulador de WebAuthn4J <strong>genera
 * pares de claves y firma de verdad</strong>. Si la verificacion del servidor estuviera
 * mal montada —origen, reto, indicadores de presencia y verificacion o firma— aqui
 * fallaria. Es el unico sitio donde se comprueba que el adaptador criptografico hace lo
 * que dice.</p>
 *
 * <p>Base de datos propia y tests ordenados: comparten el estado de la instalacion,
 * porque el alta del hogar solo funciona una vez y la passkey que registra un test la
 * usan los siguientes.</p>
 */
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:passkeys;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "gastos.webauthn.rp-id=localhost",
        "gastos.webauthn.origins=http://localhost:5173"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API de passkeys")
class PasskeyApiTest extends ApiTestSupport {

    private static final String ORIGEN = "http://localhost:5173";
    private static final String EMAIL = "titular@ejemplo.es";
    private static final String PASSWORD = "una-contrasena-larga-y-decente";

    /** El mismo autenticador durante toda la clase: guarda la credencial que registra. */
    private static final ClientPlatform NAVEGADOR = new ClientPlatform(
            new Origin(ORIGEN), new WebAuthnAuthenticatorAdaptor(new NoneAttestationAuthenticator()));

    private static String sesion;
    private static String idDeLaPasskey;
    private static String ultimaAsercion;

    @Test
    @Order(1)
    @DisplayName("se crea el hogar de partida")
    void registrarHogar() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"Casa","email":"%s","displayName":"Titular",\
                                "password":"%s","monthlyNetIncome":3400.00}"""
                                .formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        sesion = "Bearer " + objectMapper.readTree(cuerpo).get("accessToken").asText();
    }

    @Test
    @Order(2)
    @DisplayName("dar de alta una passkey exige sesion iniciada")
    void registrationRequiresSession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/passkeys/registration/options"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/passkeys"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("el navegador crea la passkey y el servidor verifica la firma del alta")
    void registersAPasskey() throws Exception {
        JsonNode opciones = opcionesDeAlta();

        // Lo que el servidor exige queda a la vista en la respuesta, no solo en el codigo.
        assertThat(opciones.get("rp").get("id").asText()).isEqualTo("localhost");
        assertThat(opciones.get("attestation").asText()).isEqualTo("none");
        assertThat(opciones.get("authenticatorSelection").get("residentKey").asText())
                .isEqualTo("required");
        assertThat(opciones.get("authenticatorSelection").get("userVerification").asText())
                .isEqualTo("required");
        // El identificador que se manda al autenticador no lleva el correo.
        assertThat(opciones.get("user").get("id").asText()).doesNotContain("titular");

        PublicKeyCredential<AuthenticatorAttestationResponse, ?> credencial =
                NAVEGADOR.create(creationOptions(opciones));
        AuthenticatorAttestationResponse respuesta = credencial.getResponse();

        String cuerpo = mockMvc.perform(post("/api/v1/auth/passkeys/registration")
                        .header(AUTHORIZATION, sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"challenge":"%s","clientDataJSON":"%s","attestationObject":"%s",\
                                "label":"iPhone de Javi"}"""
                                .formatted(opciones.get("challenge").asText(),
                                        cifrar(respuesta.getClientDataJSON()),
                                        cifrar(respuesta.getAttestationObject()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("iPhone de Javi"))
                // Ni el identificador de la credencial ni la clave publica salen de la API.
                .andExpect(jsonPath("$.credentialId").doesNotExist())
                .andExpect(jsonPath("$.attestedCredentialData").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        idDeLaPasskey = objectMapper.readTree(cuerpo).get("id").asText();
    }

    @Test
    @Order(4)
    @DisplayName("el alta aparece en el listado y trae el aparato ya excluido")
    void listsThePasskey() throws Exception {
        mockMvc.perform(get("/api/v1/auth/passkeys").header(AUTHORIZATION, sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("iPhone de Javi"))
                .andExpect(jsonPath("$[0].lastUsedAt").doesNotExist());

        // El aparato que ya esta registrado se excluye para no duplicarlo.
        assertThat(opcionesDeAlta().get("excludeCredentials").size()).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("se entra con la passkey sin escribir el correo")
    void authenticatesWithThePasskey() throws Exception {
        ultimaAsercion = asercion();

        mockMvc.perform(post("/api/v1/auth/passkeys/authentication")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ultimaAsercion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(get("/api/v1/auth/passkeys").header(AUTHORIZATION, sesion))
                .andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty());
    }

    /** Sin esto, capturar una respuesta valida bastaria para entrar cuantas veces se quisiera. */
    @Test
    @Order(6)
    @DisplayName("repetir la misma respuesta no sirve: el reto se gasta")
    void assertionCannotBeReplayed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/passkeys/authentication")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ultimaAsercion))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    @Order(7)
    @DisplayName("una firma manipulada no verifica")
    void tamperedSignatureIsRejected() throws Exception {
        JsonNode original = objectMapper.readTree(asercion());
        String firma = original.get("signature").asText();
        // Se cambia un solo caracter de la firma.
        String manipulada = (firma.charAt(0) == 'A' ? 'B' : 'A') + firma.substring(1);

        mockMvc.perform(post("/api/v1/auth/passkeys/authentication")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ((com.fasterxml.jackson.databind.node.ObjectNode) original)
                                        .put("signature", manipulada))))
                .andExpect(status().isUnauthorized());
    }

    /** OWASP API1: un token valido de otra cuenta no da acceso a los objetos de esta. */
    @Test
    @Order(8)
    @DisplayName("nadie puede borrar la passkey de otro")
    void cannotDeleteSomeoneElsesPasskey() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/passkeys/" + idDeLaPasskey)
                        .header(AUTHORIZATION, tokenForNewHousehold()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/auth/passkeys").header(AUTHORIZATION, sesion))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @Order(9)
    @DisplayName("el propietario si puede darla de baja")
    void ownerCanDeleteIt() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/passkeys/" + idDeLaPasskey)
                        .header(AUTHORIZATION, sesion))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/passkeys").header(AUTHORIZATION, sesion))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @Order(10)
    @DisplayName("la contrasena sigue funcionando: la passkey no la sustituye a la fuerza")
    void passwordStillWorks() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk());
    }

    private JsonNode opcionesDeAlta() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/passkeys/registration/options")
                        .header(AUTHORIZATION, sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    /** Pide un reto, lo firma con el autenticador emulado y devuelve el cuerpo a enviar. */
    private String asercion() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/passkeys/authentication/options"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode opciones = objectMapper.readTree(cuerpo);

        PublicKeyCredential<AuthenticatorAssertionResponse, ?> credencial = NAVEGADOR.get(
                new PublicKeyCredentialRequestOptions(
                        new DefaultChallenge(descifrar(opciones.get("challenge").asText())),
                        opciones.get("timeout").asLong(),
                        opciones.get("rpId").asText(),
                        // Lista vacia: el autenticador ofrece las credenciales que ya
                        // tiene para este dominio, que es como se entra sin el correo.
                        List.of(),
                        UserVerificationRequirement.REQUIRED,
                        null));
        AuthenticatorAssertionResponse respuesta = credencial.getResponse();

        return """
                {"challenge":"%s","credentialId":"%s","clientDataJSON":"%s",\
                "authenticatorData":"%s","signature":"%s","userHandle":"%s"}"""
                .formatted(opciones.get("challenge").asText(),
                        cifrar(credencial.getRawId()),
                        cifrar(respuesta.getClientDataJSON()),
                        cifrar(respuesta.getAuthenticatorData()),
                        cifrar(respuesta.getSignature()),
                        cifrar(respuesta.getUserHandle()));
    }

    private PublicKeyCredentialCreationOptions creationOptions(JsonNode opciones) {
        return new PublicKeyCredentialCreationOptions(
                new PublicKeyCredentialRpEntity(opciones.get("rp").get("id").asText(),
                        opciones.get("rp").get("name").asText()),
                new PublicKeyCredentialUserEntity(
                        descifrar(opciones.get("user").get("id").asText()),
                        opciones.get("user").get("name").asText(),
                        opciones.get("user").get("displayName").asText()),
                new DefaultChallenge(descifrar(opciones.get("challenge").asText())),
                List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY,
                        COSEAlgorithmIdentifier.ES256)),
                opciones.get("timeout").asLong(),
                List.of(),
                new AuthenticatorSelectionCriteria(null, ResidentKeyRequirement.REQUIRED,
                        UserVerificationRequirement.REQUIRED),
                AttestationConveyancePreference.NONE,
                null);
    }

    private static String cifrar(byte[] bytes) {
        return bytes == null ? "" : Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] descifrar(String base64url) {
        return Base64.getUrlDecoder().decode(base64url);
    }
}
