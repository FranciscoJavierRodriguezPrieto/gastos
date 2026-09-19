package com.gastos.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gastos.iam.application.port.WebAuthnCeremony;
import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.PasskeyCeremony;
import com.gastos.iam.domain.model.PasskeyChallenge;
import com.gastos.iam.domain.model.PasskeyCredential;
import com.gastos.iam.domain.model.Role;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.port.PasskeyChallengeRepository;
import com.gastos.iam.domain.port.PasskeyCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.ResourceNotFoundException;
import com.gastos.shared.domain.UserId;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reglas de las passkeys que no dependen de la criptografia.
 *
 * <p>La verificacion de firmas se prueba aparte, contra un autenticador emulado de
 * verdad. Aqui se comprueba lo que la libreria no sabe: a quien pertenece un reto, cuando
 * se consume y quien puede borrar que.</p>
 */
@DisplayName("Alta y uso de passkeys")
class PasskeyUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-03-01T10:00:00Z");
    private static final byte[] MATERIAL = {7, 7, 7};
    private static final String CREDENCIAL = "credencial-de-prueba";

    private final Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final RetosEnMemoria retos = new RetosEnMemoria();
    private final PasskeysEnMemoria passkeys = new PasskeysEnMemoria();
    private final UserRepository usuarios = mock(UserRepository.class);
    private final WebAuthnCeremony ceremonia = mock(WebAuthnCeremony.class);
    private final AuthenticateUseCase autenticacion = mock(AuthenticateUseCase.class);

    private PasskeyUseCase caso;
    private User titular;
    private AuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        caso = new PasskeyUseCase(usuarios, passkeys, retos, ceremonia, autenticacion, clock);

        titular = User.register(HouseholdId.newId(), new Email("titular@ejemplo.es"), "Javi",
                Role.OWNER, Money.euros(3400));
        principal = new AuthenticatedUser(titular.id(), titular.householdId(),
                AuthenticatedUser.ROLE_OWNER);

        when(usuarios.findById(titular.id())).thenReturn(Optional.of(titular));
        when(ceremonia.newChallenge()).thenAnswer(invocation -> UUID.randomUUID().toString());
        when(ceremonia.challengeTtl()).thenReturn(Duration.ofMinutes(5));
        when(ceremonia.relyingPartyId()).thenReturn("localhost");
        when(ceremonia.relyingPartyName()).thenReturn("Gastos");
        when(ceremonia.supportedAlgorithms()).thenReturn(List.of(-7, -257));
    }

    @Test
    @DisplayName("el identificador que se manda al autenticador no lleva datos personales")
    void userHandleIsOpaque() {
        PasskeyRegistrationOptions opciones = caso.startRegistration(principal);

        byte[] handle = Base64.getUrlDecoder().decode(opciones.userHandle());
        ByteBuffer buffer = ByteBuffer.wrap(handle);
        UUID reconstruido = new UUID(buffer.getLong(), buffer.getLong());

        assertThat(handle).hasSize(16);
        assertThat(reconstruido).isEqualTo(titular.id().value());
        assertThat(opciones.userHandle()).doesNotContain("titular@ejemplo.es");
    }

    @Test
    @DisplayName("se excluyen las passkeys que ya tiene, para no registrar dos veces el mismo aparato")
    void excludesExistingCredentials() {
        passkeys.save(unaPasskeyDe(titular.id()));

        assertThat(caso.startRegistration(principal).excludeCredentialIds())
                .containsExactly(CREDENCIAL);
    }

    /**
     * El reto del alta se consigue con la sesion iniciada; el del acceso es publico. Si
     * fueran intercambiables, el endpoint publico aceptaria retos de cualquier origen.
     */
    @Test
    @DisplayName("un reto de alta no sirve para entrar")
    void registrationChallengeIsNotValidForLogin() {
        String reto = caso.startRegistration(principal).challenge();

        assertThatThrownBy(() -> caso.finishAuthentication(reto, CREDENCIAL, "c", "a", "f", null))
                .isInstanceOf(PasskeyUseCase.InvalidPasskeyException.class);
    }

    @Test
    @DisplayName("el reto de otro usuario no sirve para dar de alta la propia passkey")
    void registrationChallengeBelongsToItsUser() {
        String reto = caso.startRegistration(principal).challenge();
        AuthenticatedUser otro = new AuthenticatedUser(UserId.newId(), titular.householdId(),
                AuthenticatedUser.ROLE_OWNER);

        assertThatThrownBy(() -> caso.finishRegistration(otro, reto, "c", "a", "Movil"))
                .isInstanceOf(PasskeyUseCase.InvalidPasskeyException.class);
    }

    /**
     * Si el reto se consumiera despues de verificar, un fallo lo dejaria vivo y se podria
     * reintentar con el mismo tantas veces como hiciera falta.
     */
    @Test
    @DisplayName("un reto se gasta aunque la verificacion falle")
    void challengeIsBurnedEvenWhenVerificationFails() {
        String reto = caso.startAuthentication().challenge();
        when(ceremonia.verifyAssertion(any(), any(), anyLong(), anyBoolean(), any(),
                any(), any(), any()))
                .thenThrow(new WebAuthnCeremony.CeremonyFailedException("firma invalida", null));
        passkeys.save(unaPasskeyDe(titular.id()));

        assertThatThrownBy(() -> caso.finishAuthentication(reto, CREDENCIAL, "c", "a", "f", null))
                .isInstanceOf(PasskeyUseCase.InvalidPasskeyException.class);

        assertThatThrownBy(() -> caso.finishAuthentication(reto, CREDENCIAL, "c", "a", "f", null))
                .isInstanceOf(PasskeyUseCase.InvalidPasskeyException.class);
        assertThat(retos.porValor(reto).usedAt()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("una credencial que dice pertenecer a otro usuario se rechaza sin llegar a verificar")
    void rejectsMismatchedUserHandle() {
        String reto = caso.startAuthentication().challenge();
        passkeys.save(unaPasskeyDe(titular.id()));
        String handleAjeno = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[16]);

        assertThatThrownBy(() -> caso.finishAuthentication(reto, CREDENCIAL, "c", "a", "f",
                handleAjeno))
                .isInstanceOf(PasskeyUseCase.InvalidPasskeyException.class);

        verify(ceremonia, never()).verifyAssertion(any(), any(), anyLong(), anyBoolean(),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("el acceso correcto avanza el contador y emite los tokens de siempre")
    void successfulAuthenticationIssuesTokens() {
        String reto = caso.startAuthentication().challenge();
        passkeys.save(unaPasskeyDe(titular.id()));
        when(ceremonia.verifyAssertion(any(), any(), anyLong(), anyBoolean(), any(),
                any(), any(), any())).thenReturn(1L);
        AuthenticationResult esperado = new AuthenticationResult("acceso", "refresco", 900,
                principal);
        when(autenticacion.issueTokensFor(titular)).thenReturn(esperado);

        AuthenticationResult resultado = caso.finishAuthentication(reto, CREDENCIAL, "c", "a", "f",
                null);

        assertThat(resultado).isEqualTo(esperado);
        assertThat(passkeys.porCredencial(CREDENCIAL).signatureCount()).isEqualTo(1);
        assertThat(passkeys.porCredencial(CREDENCIAL).lastUsedAt()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("un contador que no avanza deniega el acceso aunque la firma sea valida")
    void refusesClonedCredential() {
        String reto = caso.startAuthentication().challenge();
        passkeys.save(PasskeyCredential.register(titular.id(), CREDENCIAL, MATERIAL, 9, true,
                "Llave", AHORA));
        when(ceremonia.verifyAssertion(any(), any(), anyLong(), anyBoolean(), any(),
                any(), any(), any())).thenReturn(9L);

        assertThatThrownBy(() -> caso.finishAuthentication(reto, CREDENCIAL, "c", "a", "f", null))
                .isInstanceOf(PasskeyUseCase.InvalidPasskeyException.class);

        verify(autenticacion, never()).issueTokensFor(any());
    }

    /** OWASP API1: estar autenticado no da derecho sobre los objetos de otro. */
    @Test
    @DisplayName("nadie puede borrar la passkey de otro, ni siquiera del mismo hogar")
    void cannotDeleteSomeoneElsesPasskey() {
        PasskeyCredential ajena = unaPasskeyDe(UserId.newId());
        passkeys.save(ajena);

        assertThatThrownBy(() -> caso.delete(principal, ajena.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(passkeys.porCredencial(CREDENCIAL)).isNotNull();
    }

    @Test
    @DisplayName("el listado solo trae las propias")
    void listsOnlyOwnPasskeys() {
        passkeys.save(unaPasskeyDe(titular.id()));
        passkeys.save(PasskeyCredential.register(UserId.newId(), "otra", MATERIAL, 0, true,
                "Ajena", AHORA));

        assertThat(caso.list(principal)).singleElement()
                .extracting(PasskeyCredential::credentialId).isEqualTo(CREDENCIAL);
    }

    private static PasskeyCredential unaPasskeyDe(UserId duenno) {
        return PasskeyCredential.register(duenno, CREDENCIAL, MATERIAL, 0, true, "iPhone", AHORA);
    }

    /**
     * Dobles en memoria en vez de mocks para los dos repositorios con estado: lo que se
     * quiere comprobar es que el reto queda gastado y el contador guardado, y eso con un
     * mock habria que simularlo a mano.
     */
    private static final class RetosEnMemoria implements PasskeyChallengeRepository {

        private final Map<String, PasskeyChallenge> porValor = new HashMap<>();

        @Override
        public Optional<PasskeyChallenge> findByChallenge(String challenge) {
            return Optional.ofNullable(porValor.get(challenge));
        }

        @Override
        public PasskeyChallenge save(PasskeyChallenge challenge) {
            porValor.put(challenge.challenge(), challenge);
            return challenge;
        }

        @Override
        public int deleteExpired(Instant now) {
            return 0;
        }

        PasskeyChallenge porValor(String challenge) {
            return porValor.get(challenge);
        }
    }

    private static final class PasskeysEnMemoria implements PasskeyCredentialRepository {

        private final List<PasskeyCredential> guardadas = new ArrayList<>();

        @Override
        public Optional<PasskeyCredential> findByCredentialId(String credentialId) {
            return guardadas.stream().filter(c -> c.credentialId().equals(credentialId)).findFirst();
        }

        @Override
        public Optional<PasskeyCredential> findById(UUID id) {
            return guardadas.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<PasskeyCredential> findAllByUser(UserId userId) {
            return guardadas.stream().filter(c -> c.belongsTo(userId)).toList();
        }

        @Override
        public PasskeyCredential save(PasskeyCredential credential) {
            guardadas.removeIf(c -> c.id().equals(credential.id()));
            guardadas.add(credential);
            return credential;
        }

        @Override
        public void delete(UUID id) {
            guardadas.removeIf(c -> c.id().equals(id));
        }

        PasskeyCredential porCredencial(String credentialId) {
            return findByCredentialId(credentialId).orElse(null);
        }
    }
}
