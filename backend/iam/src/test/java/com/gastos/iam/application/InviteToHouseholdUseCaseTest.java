package com.gastos.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gastos.iam.application.port.InvitationCodeService;
import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.Household;
import com.gastos.iam.domain.model.HouseholdInvitation;
import com.gastos.iam.domain.model.InvitationCode;
import com.gastos.iam.domain.model.Role;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.model.UserCredential;
import com.gastos.iam.domain.port.HouseholdInvitationRepository;
import com.gastos.iam.domain.port.HouseholdRepository;
import com.gastos.iam.domain.port.PasswordHasher;
import com.gastos.iam.domain.port.UserCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reglas de la invitacion que no se ven desde la API.
 *
 * <p>La prueba de extremo a extremo ({@code AuthApiTest}) ya recorre el camino feliz.
 * Aqui interesan los bordes: que pasa cuando el codigo caduca, cuando el correo ya
 * existe, y en que orden se queman las cosas.</p>
 */
@DisplayName("Invitar a la pareja")
class InviteToHouseholdUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");
    private static final Duration VIDA = Duration.ofDays(7);
    private static final InvitationCode CODIGO = new InvitationCode("ABCD-EFGH-JKMN");
    private static final char[] CONTRASENA = "una-contrasena-larga-y-decente".toCharArray();

    private final InvitacionesEnMemoria invitaciones = new InvitacionesEnMemoria();
    private final HogaresEnMemoria hogares = new HogaresEnMemoria();
    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final CredencialesEnMemoria credenciales = new CredencialesEnMemoria();
    private final PasswordHasher hasher = mock(PasswordHasher.class);
    private final InvitationCodeService codigos = mock(InvitationCodeService.class);
    private final AuthenticateUseCase autenticacion = mock(AuthenticateUseCase.class);

    private Instant ahora = AHORA;
    private InviteToHouseholdUseCase caso;
    private User titular;
    private AuthenticatedUser principal;

    @BeforeEach
    void setUp() {
        // Reloj movible: la caducidad es media funcionalidad y no se puede probar con
        // un instante fijo.
        Clock clock = Clock.fixed(AHORA, ZoneOffset.UTC);
        Clock movible = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return clock.getZone();
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return ahora;
            }
        };

        titular = User.register(HouseholdId.newId(), new Email("titular@ejemplo.es"), "Titular",
                Role.OWNER, Money.euros(2200));
        usuarios.save(titular);
        hogares.save(Household.create("Nuestra casa", titular));
        principal = new AuthenticatedUser(titular.id(), titular.householdId(),
                AuthenticatedUser.ROLE_OWNER);

        when(codigos.newCode()).thenReturn(CODIGO);
        // El doble no puede llevar el codigo dentro del resultado: entonces la
        // comprobacion de que no se guarda en claro no probaria nada.
        when(codigos.hashCode(any())).thenAnswer(i ->
                Integer.toHexString(((InvitationCode) i.getArgument(0)).value().hashCode()));
        when(codigos.invitationTtl()).thenReturn(VIDA);
        when(codigos.joinUrl(any())).thenReturn("https://gastos.example/#/unirse?codigo=x");
        when(hasher.hash(any())).thenReturn("hash-de-la-contrasena");

        caso = new InviteToHouseholdUseCase(hogares, invitaciones, usuarios, credenciales, hasher,
                codigos, autenticacion, movible);
    }

    @Test
    @DisplayName("lo que se guarda es el hash, no el codigo")
    void storesTheHashNotTheCode() {
        caso.invite(principal);

        HouseholdInvitation guardada = invitaciones.todas().get(0);
        assertThat(guardada.codeHash())
                .isEqualTo(Integer.toHexString(CODIGO.value().hashCode()))
                .doesNotContain(CODIGO.value());
        assertThat(guardada.expiresAt()).isEqualTo(AHORA.plus(VIDA));
    }

    @Test
    @DisplayName("un MEMBER no puede invitar")
    void memberCannotInvite() {
        AuthenticatedUser conviviente = new AuthenticatedUser(UserId.newId(),
                titular.householdId(), "MEMBER");

        assertThatThrownBy(() -> caso.invite(conviviente))
                .isInstanceOf(ManageHouseholdUseCase.NotAllowedException.class);
    }

    @Test
    @DisplayName("el codigo deja de valer cuando pasa su vida")
    void codeExpires() {
        caso.invite(principal);

        ahora = AHORA.plus(VIDA).plusSeconds(1);

        assertThatThrownBy(() -> caso.preview(CODIGO))
                .isInstanceOf(InviteToHouseholdUseCase.InvalidInvitationException.class);
    }

    @Test
    @DisplayName("el estado no revela el codigo, solo que hay uno y hasta cuando")
    void statusNeverCarriesTheCode() {
        caso.invite(principal);

        InvitationStatus estado = caso.pendingInvitation(principal);

        assertThat(estado.pending()).isTrue();
        assertThat(estado.expiresAt()).isEqualTo(AHORA.plus(VIDA));
        assertThat(estado.householdFull()).isFalse();
        assertThat(estado.toString()).doesNotContain(CODIGO.value());
    }

    /**
     * El orden importa: si el correo repetido se detectara despues de consumir la
     * invitacion, un error corriente de formulario obligaria a pedir otro codigo.
     */
    @Test
    @DisplayName("un correo ya registrado no quema la invitacion")
    void duplicateEmailDoesNotBurnTheInvitation() {
        caso.invite(principal);

        assertThatThrownBy(() -> caso.join(CODIGO, new Email("titular@ejemplo.es"), "Copia",
                CONTRASENA, Money.euros(1800)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Ya existe un usuario con ese correo");

        // Sigue sirviendo: se corrige el correo y se vuelve a intentar.
        assertThat(caso.preview(CODIGO).householdName()).isEqualTo("Nuestra casa");
    }

    @Test
    @DisplayName("aceptarla da de alta al conviviente y deja la invitacion consumida")
    void joiningCreatesTheMember() {
        caso.invite(principal);

        caso.join(CODIGO, new Email("pareja@ejemplo.es"), "Pareja", CONTRASENA,
                Money.euros(1800));

        User conviviente = usuarios.findByEmail(new Email("pareja@ejemplo.es")).orElseThrow();
        assertThat(conviviente.role()).isEqualTo(Role.MEMBER);
        assertThat(conviviente.householdId()).isEqualTo(titular.householdId());
        assertThat(credenciales.findByUserId(conviviente.id())).isPresent();

        assertThatThrownBy(() -> caso.preview(CODIGO))
                .isInstanceOf(InviteToHouseholdUseCase.InvalidInvitationException.class);
    }

    @Test
    @DisplayName("con el hogar completo no se invita ni se acepta")
    void fullHouseholdRejectsEverything() {
        caso.invite(principal);
        caso.join(CODIGO, new Email("pareja@ejemplo.es"), "Pareja", CONTRASENA,
                Money.euros(1800));

        assertThatThrownBy(() -> caso.invite(principal))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("dos convivientes");
        assertThat(caso.pendingInvitation(principal).householdFull()).isTrue();
    }

    // --- Dobles en memoria -------------------------------------------------------

    private static final class InvitacionesEnMemoria implements HouseholdInvitationRepository {

        private final Map<String, HouseholdInvitation> porHash = new HashMap<>();

        @Override
        public Optional<HouseholdInvitation> findByCodeHash(String codeHash) {
            return Optional.ofNullable(porHash.get(codeHash));
        }

        @Override
        public Optional<HouseholdInvitation> findActiveByHousehold(HouseholdId householdId,
                                                                   Instant now) {
            return porHash.values().stream()
                    .filter(i -> i.householdId().equals(householdId) && i.isUsable(now))
                    .findFirst();
        }

        @Override
        public HouseholdInvitation save(HouseholdInvitation invitation) {
            porHash.put(invitation.codeHash(), invitation);
            return invitation;
        }

        @Override
        public void revokeAllForHousehold(HouseholdId householdId, Instant now) {
            porHash.values().stream()
                    .filter(i -> i.householdId().equals(householdId) && i.isUsable(now))
                    .forEach(i -> i.revoke(now));
        }

        List<HouseholdInvitation> todas() {
            return List.copyOf(porHash.values());
        }
    }

    private final class HogaresEnMemoria implements HouseholdRepository {

        private final Map<HouseholdId, String> nombres = new HashMap<>();

        @Override
        public Optional<Household> findById(HouseholdId householdId) {
            String nombre = nombres.get(householdId);
            if (nombre == null) {
                return Optional.empty();
            }
            // Se rehidrata con los miembros que haya ahora mismo, igual que el adaptador
            // real: el agregado necesita la lista completa para su limite de dos.
            return Optional.of(Household.rehydrate(householdId, nombre,
                    usuarios.findAllByHousehold(householdId)));
        }

        @Override
        public boolean existsAny() {
            return !nombres.isEmpty();
        }

        @Override
        public Household save(Household household) {
            nombres.put(household.id(), household.name());
            return household;
        }
    }

    private static final class UsuariosEnMemoria implements UserRepository {

        private final List<User> usuarios = new ArrayList<>();

        @Override
        public Optional<User> findById(UserId userId) {
            return usuarios.stream().filter(u -> u.id().equals(userId)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return usuarios.stream().filter(u -> u.email().equals(email)).findFirst();
        }

        @Override
        public List<User> findAllByHousehold(HouseholdId householdId) {
            return usuarios.stream().filter(u -> u.belongsTo(householdId)).toList();
        }

        @Override
        public boolean existsByEmail(Email email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public User save(User user) {
            usuarios.removeIf(u -> u.id().equals(user.id()));
            usuarios.add(user);
            return user;
        }
    }

    private static final class CredencialesEnMemoria implements UserCredentialRepository {

        private final Map<UserId, UserCredential> porUsuario = new HashMap<>();

        @Override
        public Optional<UserCredential> findByUserId(UserId userId) {
            return Optional.ofNullable(porUsuario.get(userId));
        }

        @Override
        public UserCredential save(UserCredential credential) {
            porUsuario.put(credential.userId(), credential);
            return credential;
        }
    }
}
