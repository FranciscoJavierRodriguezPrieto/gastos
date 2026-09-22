package com.gastos.iam.application;

import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.Household;
import com.gastos.iam.domain.model.PasswordPolicy;
import com.gastos.iam.domain.model.Role;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.model.UserCredential;
import com.gastos.iam.domain.port.HouseholdRepository;
import com.gastos.iam.domain.port.PasswordHasher;
import com.gastos.iam.domain.port.UserCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import java.time.Clock;
import java.util.List;

/**
 * Alta del hogar y consulta de sus miembros.
 *
 * <p><strong>No hay registro abierto.</strong> El alta inicial solo funciona mientras no
 * exista ningun hogar; a partir de ahi solo se entra con una invitacion del titular
 * ({@link InviteToHouseholdUseCase}). Es una instalacion domestica para dos personas:
 * dejar el registro abierto seria regalar una cuenta a cualquiera que encuentre la
 * URL.</p>
 */
public class ManageHouseholdUseCase {

    private final HouseholdRepository households;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordHasher passwordHasher;
    private final AuthenticateUseCase authenticate;
    private final Clock clock;

    public ManageHouseholdUseCase(HouseholdRepository households, UserRepository users,
                                  UserCredentialRepository credentials, PasswordHasher passwordHasher,
                                  AuthenticateUseCase authenticate, Clock clock) {
        this.households = Guard.notNull(households, "households");
        this.users = Guard.notNull(users, "users");
        this.credentials = Guard.notNull(credentials, "credentials");
        this.passwordHasher = Guard.notNull(passwordHasher, "passwordHasher");
        this.authenticate = Guard.notNull(authenticate, "authenticate");
        this.clock = Guard.notNull(clock, "clock");
    }

    /** Indica si la instancia todavia no tiene hogar: la pantalla inicial lo consulta. */
    public boolean needsBootstrap() {
        return !households.existsAny();
    }

    /**
     * Crea el hogar y su primer usuario, que queda como OWNER. Devuelve ya la sesion
     * iniciada para que el alta no obligue a un login inmediato.
     */
    public AuthenticationResult registerHousehold(String householdName, Email email,
                                                  String displayName, char[] rawPassword,
                                                  Money monthlyNetIncome) {
        if (households.existsAny()) {
            throw new DomainException("Esta instancia ya tiene un hogar. Pide al titular que te "
                    + "de de alta como miembro.");
        }
        PasswordPolicy.validate(rawPassword);
        Guard.notNull(email, "email");

        User owner = User.register(HouseholdId.newId(), email, displayName, Role.OWNER,
                monthlyNetIncome);
        households.save(Household.create(householdName, owner));
        users.save(owner);
        credentials.save(UserCredential.of(owner.id(), passwordHasher.hash(rawPassword),
                clock.instant()));

        return authenticate.issueTokensFor(owner);
    }

    public List<User> members(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return users.findAllByHousehold(householdId);
    }

    /** Ingresos netos agregados del hogar: numerador del DTI de la hipoteca. */
    public Money householdMonthlyIncome(HouseholdId householdId) {
        return members(householdId).stream()
                .map(User::monthlyNetIncome)
                .reduce(Money.zero(), Money::plus);
    }

    /** Se traduce a 403 en el adaptador REST. */
    public static class NotAllowedException extends RuntimeException {
        public NotAllowedException(String message) {
            super(message);
        }
    }
}
