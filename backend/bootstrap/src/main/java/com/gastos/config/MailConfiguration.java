package com.gastos.config;

import com.gastos.iam.domain.port.EmailSender;
import com.gastos.security.LoggingEmailSender;
import com.gastos.security.ResetTokenProperties;
import com.gastos.security.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Elige el emisor de correo.
 *
 * <p>La decisión se toma mirando el valor de {@code spring.mail.host} en lugar de con
 * {@code @ConditionalOnProperty}, y no es capricho: esa anotación considera «presente»
 * una propiedad con valor vacío. Como el fichero de configuración declara
 * {@code host: ${MAIL_HOST:}}, la propiedad siempre existe, y sin la variable de entorno
 * se habría activado el emisor SMTP apuntando a un servidor vacío. El síntoma habría
 * sido un fallo de conexión en cada intento, no una configuración ausente.</p>
 *
 * <p>Con un solo bean y un {@code if} explícito, lo que ocurre se lee de un vistazo.</p>
 */
@Configuration
@EnableConfigurationProperties(ResetTokenProperties.class)
public class MailConfiguration {

    @Bean
    public EmailSender emailSender(@Value("${spring.mail.host:}") String host,
                                   ObjectProvider<JavaMailSender> mailSender,
                                   ResetTokenProperties properties) {
        if (host == null || host.isBlank()) {
            return new LoggingEmailSender();
        }
        return new SmtpEmailSender(mailSender.getObject(), properties);
    }
}
