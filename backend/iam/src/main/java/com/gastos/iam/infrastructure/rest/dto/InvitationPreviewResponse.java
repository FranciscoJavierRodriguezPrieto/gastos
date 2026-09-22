package com.gastos.iam.infrastructure.rest.dto;

/**
 * Lo que se le ensena a quien llega con un codigo valido, antes de crear la cuenta.
 *
 * <p>Nombre del hogar y de quien invita, y nada mas: ni correos, ni ingresos, ni cuantos
 * miembros hay. Lo justo para que se reconozca la invitacion.</p>
 */
public record InvitationPreviewResponse(String householdName, String invitedBy) {
}
