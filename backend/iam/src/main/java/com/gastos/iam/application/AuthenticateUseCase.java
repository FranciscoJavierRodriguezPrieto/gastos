package com.gastos.iam.application;

import com.gastos.iam.application.port.TokenService;
import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.RefreshToken;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.domain.model.UserCredential;
import com.gastos.iam.domain.port.PasswordHasher;
import com.gastos.iam.domain.port.RefreshTokenRepository;
import com.gastos.iam.domain.port.UserCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.Guard;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autenticacion: iniciar sesion, refrescar y cerrar sesion.
 *
 * <p>Tres decisiones de seguridad que conviene leer juntas:</p>
 *
 * <ol>
 *   <li><strong>Un unico error para todo fallo de login.</strong> Da igual que el correo
 *       no exista, que no tenga credencial o que la contrasena sea incorrecta: mismo
 *       mensaje. Distinguirlos convertiria el login en un comprobador de correos
 *       registrados.</li>
 *   <li><strong>Se calcula el hash aunque el usuario no exista.</strong> Sin esto, un
 *       correo desconocido responderia en microsegundos y uno valido en decenas de
 *       milisegundos: el tiempo de respuesta delataria que cuentas existen.</li>
 *   <li><strong>Reutilizar un token de refresco revoca toda la sesion.</strong> Un token
 *       ya consumido que reaparece solo tiene una explicacion razonable: alguien lo
 *       copio. Ante la duda, fuera todos.</li>
 * </ol>
 */
public class AuthenticateUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthenticateUseCase.class);

    /** Hash de descarte con el que igualar tiempos cuando el usuario no existe. */
    private static final char[] DUMMY_PASSWORD = "contrasena-de-descarte".toCharArray();

    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final Clock clock;

    public AuthenticateUseCase(UserRepository users, UserCredentialRepository credentials,
                               RefreshTokenRepository refreshTokens, PasswordHasher passwordHasher,
                               TokenService tokenService, Clock clock) {
        this.users = Guard.notNull(users, "users");
        this.credentials = Guard.notNull(credentials, "credentials");
        this.refreshTokens = Guard.notNull(refreshTokens, "refreshTokens");
        this.passwordHasher = Guard.notNull(passwordHasher, "passwordHasher");
        this.tokenService = Guard.notNull(tokenService, "tokenService");
        this.clock = Guard.notNull(clock, "clock");
    }

    public AuthenticationResult login(Email email, char[] rawPassword) {
        Guard.notNull(email, "email");
        Optional<User> user = users.findByEmail(email);
        Optional<UserCredential> credential = user.flatMap(u -> credentials.findByUserId(u.id()));

        if (credential.isEmpty()) {
            // Se gasta el mismo tiempo que en una comprobacion real para no delatar por
            // latencia si el correo existe.
            passwordHasher.matches(rawPassword, passwordHasher.hash(DUMMY_PASSWORD));
            log.info("Intento de login fallido: credenciales no validas");
            throw new InvalidCredentialsException();
        }

        if (!passwordHasher.matches(rawPassword, credential.get().passwordHash())) {
            log.info("Intento de login fallido para el usuario {}", user.get().id());
            throw new InvalidCredentialsException();
        }

        log.info("Login correcto del usuario {}", user.get().id());
        return issueTokensFor(user.get());
    }

    /** Refresco con rotacion: el token presentado se consume y se emite uno nuevo. */
    public AuthenticationResult refresh(String rawRefreshToken) {
        Guard.notBlank(rawRefreshToken, "refreshToken");
        Instant now = clock.instant();

        RefreshToken stored = refreshTokens.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
                .orElseThrow(InvalidCredentialsException::new);

        if (stored.isRevoked()) {
            // Reutilizacion: el token ya se habia canjeado. Se cae toda la cadena.
            log.warn("Reutilizacion de token de refresco del usuario {}: se revoca la sesion",
                    stored.userId());
            refreshTokens.revokeAllForUser(stored.userId(), now);
            throw new InvalidCredentialsException();
        }
        if (!stored.isUsable(now)) {
            throw new InvalidCredentialsException();
        }

        User user = users.findById(stored.userId()).orElseThrow(InvalidCredentialsException::new);

        String rawReplacement = tokenService.newRefreshToken();
        RefreshToken replacement = RefreshToken.issue(user.id(),
                tokenService.hashRefreshToken(rawReplacement), now,
                now.plus(tokenService.refreshTokenTtl()));
        refreshTokens.save(replacement);

        stored.rotateTo(replacement, now);
        refreshTokens.save(stored);

        return new AuthenticationResult(
                tokenService.issueAccessToken(principalOf(user)),
                rawReplacement,
                tokenService.accessTokenTtl().toSeconds(),
                principalOf(user));
    }

    /**
     * Cierra la sesion revocando el token de refresco.
     *
     * <p>No falla si el token no existe o ya estaba revocado: cerrar sesion siempre debe
     * salir bien desde el punto de vista del cliente, y responder distinto convertiria
     * este endpoint en un oraculo de tokens validos.</p>
     */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    refreshTokens.save(token);
                });
    }

    /** Emite la pareja de tokens para un usuario ya verificado. */
    AuthenticationResult issueTokensFor(User user) {
        Instant now = clock.instant();
        String rawRefreshToken = tokenService.newRefreshToken();

        refreshTokens.save(RefreshToken.issue(user.id(),
                tokenService.hashRefreshToken(rawRefreshToken), now,
                now.plus(tokenService.refreshTokenTtl())));

        AuthenticatedUser principal = principalOf(user);
        return new AuthenticationResult(
                tokenService.issueAccessToken(principal),
                rawRefreshToken,
                tokenService.accessTokenTtl().toSeconds(),
                principal);
    }

    private static AuthenticatedUser principalOf(User user) {
        return new AuthenticatedUser(user.id(), user.householdId(), user.role().name());
    }

    /** Se traduce a 401 en el adaptador REST, siempre con el mismo mensaje. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Credenciales no validas");
        }
    }
}
