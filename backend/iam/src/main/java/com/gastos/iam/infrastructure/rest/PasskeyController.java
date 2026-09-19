package com.gastos.iam.infrastructure.rest;

import com.gastos.iam.application.PasskeyUseCase;
import com.gastos.iam.infrastructure.rest.dto.AuthenticateWithPasskeyRequest;
import com.gastos.iam.infrastructure.rest.dto.PasskeyAuthenticationOptionsResponse;
import com.gastos.iam.infrastructure.rest.dto.PasskeyRegistrationOptionsResponse;
import com.gastos.iam.infrastructure.rest.dto.PasskeyResponse;
import com.gastos.iam.infrastructure.rest.dto.RegisterPasskeyRequest;
import com.gastos.iam.infrastructure.rest.dto.TokenResponse;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.web.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passkeys: alta, listado, borrado y acceso.
 *
 * <p>De los seis endpoints solo dos son publicos, los dos del acceso, y no pueden ser de
 * otra forma: quien entra con passkey todavia no tiene token. Los otros cuatro tocan las
 * credenciales de una cuenta concreta y exigen sesion iniciada.</p>
 *
 * <p>Los campos binarios viajan en base64url, no en base64 normal. Es lo que produce y
 * espera el propio navegador, y ademas evita los caracteres que habria que escapar en una
 * URL o en JSON.</p>
 */
@RestController
@RequestMapping("/api/v1/auth/passkeys")
public class PasskeyController {

    private final PasskeyUseCase passkeys;

    public PasskeyController(PasskeyUseCase passkeys) {
        this.passkeys = passkeys;
    }

    /** Paso 1 del alta: parametros y reto. Exige sesion. */
    @PostMapping("/registration/options")
    public PasskeyRegistrationOptionsResponse registrationOptions(
            @CurrentUser AuthenticatedUser user) {
        return PasskeyRestMapper.toResponse(passkeys.startRegistration(user));
    }

    /** Paso 2 del alta: se guarda la clave publica si la respuesta verifica. */
    @PostMapping("/registration")
    public ResponseEntity<PasskeyResponse> register(@CurrentUser AuthenticatedUser user,
                                                    @Valid @RequestBody RegisterPasskeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PasskeyRestMapper.toResponse(
                passkeys.finishRegistration(user, request.challenge(), request.clientDataJSON(),
                        request.attestationObject(), request.label())));
    }

    /** Paso 1 del acceso. Publico: aun no se sabe quien llama. */
    @PostMapping("/authentication/options")
    public PasskeyAuthenticationOptionsResponse authenticationOptions() {
        return PasskeyRestMapper.toResponse(passkeys.startAuthentication());
    }

    /** Paso 2 del acceso: devuelve los mismos tokens que un login con contrasena. */
    @PostMapping("/authentication")
    public TokenResponse authenticate(@Valid @RequestBody AuthenticateWithPasskeyRequest request) {
        return AuthRestMapper.toResponse(passkeys.finishAuthentication(
                request.challenge(), request.credentialId(), request.clientDataJSON(),
                request.authenticatorData(), request.signature(), request.userHandle()));
    }

    @GetMapping
    public List<PasskeyResponse> list(@CurrentUser AuthenticatedUser user) {
        return PasskeyRestMapper.toResponses(passkeys.list(user));
    }

    /** Borrado. Solo las propias: el caso de uso comprueba la pertenencia. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser user,
                                       @PathVariable UUID id) {
        passkeys.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
