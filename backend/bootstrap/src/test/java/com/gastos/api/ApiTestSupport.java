package com.gastos.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gastos.iam.application.port.TokenService;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base de las pruebas de API autenticadas.
 *
 * <p>Cada test trabaja con un hogar recien inventado y su token. Como todos los datos
 * estan acotados por hogar, eso da <strong>aislamiento entre tests sin limpiar la base de
 * datos</strong>: ninguno ve lo que escribio otro.</p>
 *
 * <p>Los tokens se firman con la clave real de la aplicacion. No se falsea la
 * autenticacion: lo que se prueba es la <em>autorizacion</em>, y para eso hace falta que
 * el token sea autentico. Un token valido de otro hogar es exactamente el escenario del
 * que protege OWASP API1.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTestSupport {

    protected static final String AUTHORIZATION = "Authorization";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    /**
     * Token del hogar de este test.
     *
     * <p>Se emite en {@code @BeforeEach} y no en la declaracion del campo: los
     * inicializadores corren en el constructor, antes de que Spring inyecte nada.</p>
     */
    protected String TOKEN;

    @BeforeEach
    void issueDefaultToken() {
        TOKEN = tokenForNewHousehold();
    }

    /** Token de un hogar nuevo, con rol de titular. */
    protected String tokenForNewHousehold() {
        return bearer(new AuthenticatedUser(UserId.newId(), HouseholdId.newId(),
                AuthenticatedUser.ROLE_OWNER));
    }

    /** Token de otro usuario del mismo hogar, util para probar permisos por rol. */
    protected String tokenForMemberOf(String ownerToken) {
        return bearer(new AuthenticatedUser(UserId.newId(), householdOf(ownerToken), "MEMBER"));
    }

    protected String bearer(AuthenticatedUser user) {
        return "Bearer " + tokenService.issueAccessToken(user);
    }

    /** Recupera el hogar de un token ya emitido, para encadenar identidades en un test. */
    private HouseholdId householdOf(String bearerToken) {
        String[] parts = bearerToken.replace("Bearer ", "").split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        try {
            return HouseholdId.of(objectMapper.readTree(payload).get("hid").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Token de prueba mal formado", e);
        }
    }
}
