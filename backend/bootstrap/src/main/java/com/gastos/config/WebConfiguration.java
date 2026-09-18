package com.gastos.config;

import com.gastos.web.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfiguration(@Value("${gastos.cors.allowed-origins:}") List<String> allowedOrigins,
                            CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.allowedOrigins = allowedOrigins;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
