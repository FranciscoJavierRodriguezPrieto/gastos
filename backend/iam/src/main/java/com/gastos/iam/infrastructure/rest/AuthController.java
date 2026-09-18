package com.gastos.iam.infrastructure.rest;

import com.gastos.iam.application.AuthenticateUseCase;
import com.gastos.iam.application.AuthenticationResult;
import com.gastos.iam.application.ManageHouseholdUseCase;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.infrastructure.rest.dto.AddMemberRequest;
import com.gastos.iam.infrastructure.rest.dto.BootstrapStatusResponse;
import com.gastos.iam.infrastructure.rest.dto.LoginRequest;
import com.gastos.iam.infrastructure.rest.dto.RefreshRequest;
import com.gastos.iam.infrastructure.rest.dto.RegisterHouseholdRequest;
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
 * refresco. Todo lo demas de la API exige un token de acceso valido.</p>
 *
 * <p>Los tokens se devuelven en el cuerpo y no en una cookie: la API es sin estado y el
 * cliente los envia en {@code Authorization}. Al no usar cookies, el vector de CSRF
 * simplemente no existe.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticateUseCase authenticate;
    private final ManageHouseholdUseCase household;

    public AuthController(AuthenticateUseCase authenticate, ManageHouseholdUseCase household) {
        this.authenticate = authenticate;
        this.household = household;
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

    @GetMapping("/me")
    public UserResponse me(@CurrentUser AuthenticatedUser user) {
        return household.members(user.householdId()).stream()
                .filter(member -> member.id().equals(user.userId()))
                .findFirst()
                .map(AuthRestMapper::toResponse)
                .orElseThrow(() -> new com.gastos.shared.domain.ResourceNotFoundException(
                        "Usuario no encontrado"));
    }

    @GetMapping("/members")
    public List<UserResponse> members(@CurrentUser AuthenticatedUser user) {
        return AuthRestMapper.toResponses(household.members(user.householdId()));
    }

    @PostMapping("/members")
    public ResponseEntity<UserResponse> addMember(@CurrentUser AuthenticatedUser user,
                                                  @Valid @RequestBody AddMemberRequest request) {
        User member = household.addMember(
                user,
                new com.gastos.iam.domain.model.Email(request.email()),
                request.displayName(),
                request.password().toCharArray(),
                Money.euros(request.monthlyNetIncome()));

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthRestMapper.toResponse(member));
    }
}
