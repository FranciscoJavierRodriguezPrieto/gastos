package com.gastos.security;

import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Envio por rele SMTP.
 *
 * <p>Se usa SMTP y no la API REST del proveedor a proposito: SMTP es un contrato
 * universal, asi que cambiar de Brevo a otro proveedor es cambiar tres lineas de
 * configuracion en vez de reescribir un cliente HTTP. El dominio ni se entera: solo ve
 * el puerto {@link EmailSender}.</p>
 *
 * <p>Solo se instancia si hay servidor configurado; lo decide {@code MailConfiguration}.
 * Sin el entra {@link LoggingEmailSender}, para que la aplicacion arranque igual en un
 * entorno de desarrollo sin correo.</p>
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final ResetTokenProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, ResetTokenProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(Email destinatario, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(properties.from());
        mensaje.setTo(destinatario.value());
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);

        try {
            mailSender.send(mensaje);
        } catch (RuntimeException e) {
            // No se propaga: si el envio falla, el usuario recibiria un error distinto al
            // de un correo inexistente y eso delataria que cuentas existen. Queda en el
            // log, que es donde hay que mirarlo.
            log.error("No se ha podido enviar el correo de restablecimiento", e);
        }
    }
}
