package com.gastos.iam.infrastructure.rest.dto;

import java.time.Instant;

/**
 * Invitacion recien generada.
 *
 * <p>Es la <strong>unica</strong> respuesta de toda la API que contiene el codigo. No se
 * guarda en claro, asi que no hay forma de volver a consultarlo: la pantalla tiene que
 * ensenarlo en ese momento y decirlo claramente.</p>
 *
 * @param code      ya formateado en grupos de cuatro, para leerlo o dictarlo
 * @param joinUrl   enlace directo a la pantalla de alta con el codigo dentro
 */
public record InvitationResponse(String code, Instant expiresAt, String joinUrl) {
}
