package com.gastos.iam.infrastructure.rest;

import com.gastos.iam.application.InvitationPreview;
import com.gastos.iam.application.InvitationStatus;
import com.gastos.iam.application.InviteToHouseholdUseCase;
import com.gastos.iam.application.IssuedInvitation;
import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.model.InvitationCode;
import com.gastos.iam.infrastructure.rest.dto.CheckInvitationRequest;
import com.gastos.iam.infrastructure.rest.dto.InvitationPreviewResponse;
import com.gastos.iam.infrastructure.rest.dto.InvitationResponse;
import com.gastos.iam.infrastructure.rest.dto.InvitationStatusResponse;
import com.gastos.iam.infrastructure.rest.dto.JoinHouseholdRequest;
import com.gastos.iam.infrastructure.rest.dto.TokenResponse;
import com.gastos.shared.domain.AuthenticatedUser;
import com.gastos.shared.domain.Money;
import com.gastos.shared.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitar a la pareja y darse de alta con el codigo.
 *
 * <p>Tres endpoints exigen sesion de titular —generar, consultar y revocar— y dos son
 * publicos: comprobar el codigo y el alta. No pueden ser de otro modo, igual que pasa
 * con el acceso por passkey: quien se esta dando de alta todavia no tiene token, y lo
 * unico que lo autoriza es el codigo.</p>
 *
 * <p>Los dos publicos reciben el codigo <strong>en el cuerpo</strong> y no en la ruta.
 * Un codigo en la URL aparece en los registros de acceso del servidor, del proxy y de
 * cualquier intermediario, y esto es una credencial con la que se entra en el hogar.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class InvitationController {

    private final InviteToHouseholdUseCase invitations;

    public InvitationController(InviteToHouseholdUseCase invitations) {
        this.invitations = invitations;
    }

    /**
     * Genera el codigo. <strong>Unica respuesta de la API que lo contiene</strong>: solo
     * se guarda su hash, asi que no se puede volver a consultar.
     */
    @PostMapping("/invitations")
    public ResponseEntity<InvitationResponse> invite(@CurrentUser AuthenticatedUser user) {
        IssuedInvitation emitida = invitations.invite(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(new InvitationResponse(
                emitida.code().formatted(), emitida.expiresAt(), emitida.joinUrl()));
    }

    /** Si hay invitacion vigente y hasta cuando. Sin el codigo. */
    @GetMapping("/invitations")
    public InvitationStatusResponse pending(@CurrentUser AuthenticatedUser user) {
        InvitationStatus estado = invitations.pendingInvitation(user);
        return new InvitationStatusResponse(estado.pending(), estado.expiresAt(),
                estado.householdFull());
    }

    /** Anula la invitacion vigente. Responde 204 aunque no hubiera ninguna. */
    @DeleteMapping("/invitations")
    public ResponseEntity<Void> revoke(@CurrentUser AuthenticatedUser user) {
        invitations.revoke(user);
        return ResponseEntity.noContent().build();
    }

    /** Publico: valida el codigo y dice a que hogar se entra y de parte de quien. */
    @PostMapping("/invitations/check")
    public InvitationPreviewResponse check(@Valid @RequestBody CheckInvitationRequest request) {
        InvitationPreview vista = invitations.preview(new InvitationCode(request.code()));
        return new InvitationPreviewResponse(vista.householdName(), vista.invitedBy());
    }

    /** Publico: alta con el codigo. Devuelve la sesion ya iniciada. */
    @PostMapping("/join")
    public ResponseEntity<TokenResponse> join(@Valid @RequestBody JoinHouseholdRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthRestMapper.toResponse(
                invitations.join(
                        new InvitationCode(request.code()),
                        new Email(request.email()),
                        request.displayName(),
                        request.password().toCharArray(),
                        Money.euros(request.monthlyNetIncome()))));
    }
}
