package com.gastos.iam.application;

import com.gastos.iam.application.port.InvitationCodeService;
import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.Household;
import com.gastos.iam.domain.model.HouseholdInvitation;
import com.gastos.iam.domain.model.InvitationCode;
import com.gastos.iam.domain.model.PasswordPolicy;
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
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invitar a la pareja y que se de de alta ella misma con el codigo.
 *
 * <p>Hasta ahora el titular creaba la cuenta del otro conviviente <em>y le ponia la
 * contrasena</em>. Funcionaba, pero dejaba a una persona conociendo la credencial de
 * otra. Este caso de uso parte el alta en dos: el titular genera un codigo, y quien lo
 * recibe elige su propio correo y su propia contrasena.</p>
 *
 * <p>Cuatro decisiones de seguridad:</p>
 *
 * <ol>
 *   <li><strong>El codigo es la autorizacion.</strong> El alta con codigo es publica
 *       —quien se da de alta todavia no tiene sesion—, asi que lo unico que separa a un
 *       desconocido del hogar es tener el codigo. De ahi que sea de un solo uso, caduque
 *       en dias y solo exista una invitacion viva a la vez.</li>
 *   <li><strong>Se guarda el hash, no el codigo.</strong> Como con los tokens de refresco
 *       y de restablecimiento. El precio es que el codigo solo se puede ensenar una vez;
 *       el beneficio es que la tabla no es una lista de llaves del hogar.</li>
 *   <li><strong>Generar un codigo invalida el anterior.</strong> Pedirlo dos veces no
 *       deja dos puertas abiertas.</li>
 *   <li><strong>El limite de dos convivientes se comprueba dos veces</strong>: al emitir,
 *       para no dar un codigo que no va a servir, y al aceptar, que es cuando de verdad
 *       importa. Entre una cosa y otra pueden pasar dias.</li>
 * </ol>
 */
public class InviteToHouseholdUseCase {

    private static final Logger log = LoggerFactory.getLogger(InviteToHouseholdUseCase.class);

    private final HouseholdRepository households;
    private final HouseholdInvitationRepository invitations;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final InvitationCodeService codeService;
    private final AuthenticateUseCase authenticate;
    private final Clock clock;

    public InviteToHouseholdUseCase(HouseholdRepository households,
                                    HouseholdInvitationRepository invitations,
                                    UserRepository users, UserCredentialRepository credentials,
                                    PasswordHasher passwordHasher,
                                    InvitationCodeService codeService,
                                    AuthenticateUseCase authenticate, Clock clock) {
        this.households = Guard.notNull(households, "households");
        this.invitations = Guard.notNull(invitations, "invitations");
        this.users = Guard.notNull(users, "users");
        this.credentials = Guard.notNull(credentials, "credentials");
        this.passwordHasher = Guard.notNull(passwordHasher, "passwordHasher");
        this.codeService = Guard.notNull(codeService, "codeService");
        this.authenticate = Guard.notNull(authenticate, "authenticate");
        this.clock = Guard.notNull(clock, "clock");
    }

    /**
     * Genera el codigo de invitacion. Solo el titular.
     *
     * @return el codigo en claro; no se puede volver a consultar
     */
    public IssuedInvitation invite(AuthenticatedUser requester) {
        Household household = soloElTitular(requester);

        if (estaCompleto(household)) {
            throw new DomainException("El hogar ya tiene sus dos convivientes");
        }

        Instant now = clock.instant();
        // Una invitacion nueva deja sin efecto la anterior: solo debe haber un codigo
        // en circulacion.
        invitations.revokeAllForHousehold(household.id(), now);

        InvitationCode code = codeService.newCode();
        Instant expiresAt = now.plus(codeService.invitationTtl());
        invitations.save(HouseholdInvitation.issue(household.id(), requester.userId(),
                codeService.hashCode(code), now, expiresAt));

        log.info("Invitacion emitida para el hogar {} por el usuario {}", household.id(),
                requester.userId());
        return new IssuedInvitation(code, expiresAt, codeService.joinUrl(code));
    }

    /** Si hay invitacion vigente y hasta cuando. Nunca devuelve el codigo. */
    public InvitationStatus pendingInvitation(AuthenticatedUser requester) {
        Household household = soloElTitular(requester);
        boolean completo = estaCompleto(household);

        return invitations.findActiveByHousehold(household.id(), clock.instant())
                .map(invitacion -> new InvitationStatus(true, invitacion.expiresAt(), completo))
                .orElseGet(() -> InvitationStatus.none(completo));
    }

    /** Anula la invitacion vigente, si la hay. Solo el titular. */
    public void revoke(AuthenticatedUser requester) {
        Household household = soloElTitular(requester);
        invitations.revokeAllForHousehold(household.id(), clock.instant());
        log.info("Invitaciones del hogar {} revocadas por el usuario {}", household.id(),
                requester.userId());
    }

    /**
     * Comprueba el codigo antes de pedir datos, para que la pantalla de alta pueda decir
     * a que hogar se entra y quien invita.
     */
    public InvitationPreview preview(InvitationCode code) {
        HouseholdInvitation invitacion = invitacionVigente(code);
        Household household = households.findById(invitacion.householdId())
                .orElseThrow(InvalidInvitationException::new);

        String invitadoPor = users.findById(invitacion.invitedBy())
                .map(User::displayName)
                .orElse("el titular del hogar");

        return new InvitationPreview(household.name(), invitadoPor);
    }

    /**
     * Alta del segundo conviviente a partir del codigo. Devuelve la sesion ya iniciada,
     * igual que el alta del hogar: quien acaba de escribir su contrasena no deberia
     * tener que escribirla otra vez.
     */
    public AuthenticationResult join(InvitationCode code, Email email, String displayName,
                                     char[] rawPassword, Money monthlyNetIncome) {
        Guard.notNull(email, "email");
        PasswordPolicy.validate(rawPassword);

        Instant now = clock.instant();
        HouseholdInvitation invitacion = invitacionVigente(code);

        Household household = households.findById(invitacion.householdId())
                .orElseThrow(() -> new ResourceNotFoundException("Hogar no encontrado"));

        // Las dos comprobaciones que pueden fallar van ANTES de consumir la invitacion:
        // si fallaran despues, el codigo quedaria quemado y habria que pedir otro por un
        // correo repetido o por llegar tarde.
        if (users.existsByEmail(email)) {
            throw new DomainException("Ya existe un usuario con ese correo");
        }
        if (estaCompleto(household)) {
            throw new DomainException("El hogar ya tiene sus dos convivientes");
        }

        User member = User.register(household.id(), email, displayName, Role.MEMBER,
                monthlyNetIncome);
        // El limite lo impone el agregado, no este metodo: asi la regla se cumple venga
        // la peticion de donde venga.
        household.addMember(member);

        // Consumir primero y crear despues. Si el orden fuera el inverso y algo fallara
        // entre medias, el codigo seguiria vivo con una cuenta ya creada: dos altas con
        // la misma invitacion.
        invitacion.accept(now);
        invitations.save(invitacion);
        invitations.revokeAllForHousehold(household.id(), now);

        households.save(household);
        users.save(member);
        credentials.save(UserCredential.of(member.id(), passwordHasher.hash(rawPassword), now));

        log.info("Usuario {} dado de alta en el hogar {} mediante invitacion", member.id(),
                household.id());
        return authenticate.issueTokensFor(member);
    }

    private HouseholdInvitation invitacionVigente(InvitationCode code) {
        Guard.notNull(code, "codigo");
        HouseholdInvitation invitacion = invitations.findByCodeHash(codeService.hashCode(code))
                .orElseThrow(InvalidInvitationException::new);
        if (!invitacion.isUsable(clock.instant())) {
            throw new InvalidInvitationException();
        }
        return invitacion;
    }

    private Household soloElTitular(AuthenticatedUser requester) {
        Guard.notNull(requester, "requester");
        if (!requester.isOwner()) {
            throw new ManageHouseholdUseCase.NotAllowedException(
                    "Solo el titular del hogar puede invitar");
        }
        return households.findById(requester.householdId())
                .orElseThrow(() -> new ResourceNotFoundException("Hogar no encontrado"));
    }

    private boolean estaCompleto(Household household) {
        return household.members().size() >= Household.MAX_MEMBERS;
    }

    /**
     * Codigo desconocido, caducado, revocado o ya usado.
     *
     * <p>Un unico error para los cuatro casos, y con el mismo texto: distinguirlos
     * convertiria la pantalla de alta en un comprobador de codigos. Se traduce a 400 en
     * el adaptador REST.</p>
     */
    public static class InvalidInvitationException extends RuntimeException {
        public InvalidInvitationException() {
            super("El codigo de invitacion no es valido o ha caducado");
        }
    }
}
