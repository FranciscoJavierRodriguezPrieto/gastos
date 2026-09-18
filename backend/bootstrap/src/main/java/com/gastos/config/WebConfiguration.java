package com.gastos.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracion de la capa web.
 *
 * <p>CORS declarado de forma explicita y restrictiva (OWASP API8): solo los origenes
 * que se listen en {@code gastos.cors.allowed-origins}, y por defecto ninguno salvo el
 * servidor de desarrollo del frontend. Nunca comodin, y menos con credenciales.</p>
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfiguration(@Value("${gastos.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Authorization", "X-Household-Id", "X-User-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
