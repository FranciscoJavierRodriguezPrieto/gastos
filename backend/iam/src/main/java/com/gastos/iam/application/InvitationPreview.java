package com.gastos.iam.application;

/**
 * Lo que ve quien llega con un codigo, antes de crear su cuenta.
 *
 * <p>Se ensena el nombre del hogar y el de quien invita para que se pueda comprobar que
 * el codigo es el que esperaba. Es lo unico que se revela: ni correos, ni ingresos, ni
 * cuantos miembros hay.</p>
 */
public record InvitationPreview(String householdName, String invitedBy) {
}
