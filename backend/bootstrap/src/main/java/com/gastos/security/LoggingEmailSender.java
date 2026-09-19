package com.gastos.security;

import com.gastos.iam.domain.model.Email;
import com.gastos.iam.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Respaldo cuando no hay servidor de correo configurado.
 *
 * <p>Existe para que la aplicacion arranque en desarrollo sin obligar a montar SMTP, y
 * para que los tests no necesiten un servidor de correo.</p>
 *
 * <p><strong>Avisa en cada arranque y en cada envio</strong>, con nivel WARN. Un
 * respaldo silencioso seria peor que no tenerlo: alguien desplegaria sin correo, el
 * restablecimiento respondería correctamente —porque no puede distinguir casos— y nadie
 * se enteraria de que ningun enlace llega a su destino.</p>
 *
 * <p>El enlace NO se escribe en el log: seria una credencial valida en texto plano al
 * alcance de cualquiera que lea las trazas.</p>
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    public LoggingEmailSender() {
        log.warn("No hay servidor de correo configurado (spring.mail.host). El restablecimiento "
                + "de contrasena NO enviara ningun mensaje. Configuralo antes de desplegar.");
    }

    @Override
    public void send(Email destinatario, String asunto, String cuerpo) {
        log.warn("Correo NO enviado por falta de configuracion SMTP. Asunto: {}", asunto);
    }
}
