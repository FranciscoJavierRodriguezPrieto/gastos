package com.gastos.iam.infrastructure.rest;

import com.gastos.iam.application.AuthenticateUseCase;
import com.gastos.iam.application.AuthenticationResult;
import com.gastos.iam.application.ManageHouseholdUseCase;
import com.gastos.iam.application.RecoverAccessUseCase;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.infrastructure.rest.dto.BootstrapStatusResponse;
import com.gastos.iam.infrastructure.rest.dto.ChangePasswordRequest;
import com.gastos.iam.infrastructure.rest.dto.ForgotPasswordRequest;
import com.gastos.iam.infrastructure.rest.dto.LoginRequest;
import com.gastos.iam.infrastructure.rest.dto.RefreshRequest;
import com.gastos.iam.infrastructure.rest.dto.RegisterHouseholdRequest;
import com.gastos.iam.infrastructure.rest.dto.ResetPasswordRequest;
import com.gastos.iam.infrastructure.rest.dto.TokenResponse;
import com.gastos.iam.infrastructure.rest.dto.UserResponse;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.Money;
import com.gastos.shared.web.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticacion y gestion del hogar.
 *
 * <p>Los tres endpoints publicos son {@code /status}, {@code /register} y {@code /login},
 * mas {@code /refresh} y {@code /logout} que se autentican con el propio token de
 * refresco. Todo lo demas de la API exige un token de acceso valido. El alta del segundo
 * conviviente tambien es publica, pero vive en {@link InvitationController}: lo que la
 * autoriza es el codigo de invitacion, no una sesion.</p>
 *
 * <p>Los tokens se devuelven en el cuerpo y no en una cookie: la API es sin estado y el
 * cliente los envia en {@code Authorization}. Al no usar cookies, el vector de CSRF
 * simplemente no existe.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final AuthenticateUseCase authenticate;
    private final ManageHouseholdUseCase household;
    private final RecoverAccessUseCase recover;

    public AuthController(AuthenticateUseCase authenticate, ManageHouseholdUseCase household,
                          RecoverAccessUseCase recover) {
        this.authenticate = authenticate;
        this.household = household;
        this.recover = recover;
    }

    /** Permite a la pantalla inicial saber si hay que crear el hogar o pedir login. */
    @GetMapping("/status")
    public BootstrapStatusResponse status() {
        return new BootstrapStatusResponse(household.needsBootstrap());
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterHouseholdRequest request) {
        AuthenticationResult result = household.registerHousehold(
                request.householdName(),
                new com.gastos.iam.domain.model.Email(request.email()),
                request.displayName(),
                request.password().toCharArray(),
                Money.euros(request.monthlyNetIncome()));

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthRestMapper.toResponse(result));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthRestMapper.toResponse(authenticate.login(
                new com.gastos.iam.domain.model.Email(request.email()),
                request.password().toCharArray()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return AuthRestMapper.toResponse(authenticate.refresh(request.refreshToken()));
    }

    /** Siempre responde 204, exista o no el token: no es un oraculo de tokens validos. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authenticate.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Pide el enlace de restablecimiento.
     *
     * <p>Responde <strong>204 siempre</strong>, exista el correo o no. Cualquier otra
     * cosa convertiria este formulario en un comprobador de cuentas registradas.</p>
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            recover.requestReset(new com.gastos.iam.domain.model.Email(request.email()));
        } catch (com.gastos.shared.domain.DomainException e) {
            // Un correo con formato invalido no puede estar registrado. Se traga el
            // error para que la respuesta sea idéntica a la de un correo desconocido.
            log.info("Solicitud de restablecimiento con un correo mal formado");
        }
        return ResponseEntity.noContent().build();
    }

    /** Fija la contrasena nueva y deja la sesion iniciada. */
    @PostMapping("/reset-password")
    public TokenResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        User usuario = recover.resetPassword(request.token(), request.newPassword().toCharArray());
        return AuthRestMapper.toResponse(authenticate.issueTokensFor(usuario));
    }

    /** Cambia la contrasena con la sesion ya iniciada. Exige la actual. */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@CurrentUser AuthenticatedUser user,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        recover.changePassword(user, request.currentPassword().toCharArray(),
                request.newPassword().toCharArray());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser AuthenticatedUser user) {
        return household.members(user.householdId()).stream()
                .filter(member -> member.id().equals(user.userId()))
                .findFirst()
                .map(AuthRestMapper::toResponse)
                .orElseThrow(() -> new com.gastos.shared.domain.ResourceNotFoundException(
                        "Usuario no encontrado"));
    }

    /**
     * Miembros del hogar. Solo de lectura: el segundo conviviente entra por invitacion
     * ({@link InvitationController}), no dado de alta por el titular.
     */
    @GetMapping("/members")
    public List<UserResponse> members(@CurrentUser AuthenticatedUser user) {
        return AuthRestMapper.toResponses(household.members(user.householdId()));
    }
}
