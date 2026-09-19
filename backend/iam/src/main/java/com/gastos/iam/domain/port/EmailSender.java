package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.Email;

/**
 * Puerto de envio de correo.
 *
 * <p>El dominio dice QUE hay que enviar, no como. Que detras haya un rele SMTP de Brevo,
 * otro proveedor o un doble de pruebas no cambia ni una regla de negocio.</p>
 */
public interface EmailSender {

    void send(Email destinatario, String asunto, String cuerpo);
}
