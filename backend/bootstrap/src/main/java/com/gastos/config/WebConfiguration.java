package com.gastos.config;

import com.gastos.web.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuracion de la capa web.
 *
 * <p>CORS se publica como {@link CorsConfigurationSource} y no con
 * {@code addCorsMappings}, y la diferencia no es cosmetica: la peticion de sondeo
 * (<em>preflight</em>) que el navegador manda antes de un POST con JSON llega sin token, y
 * la cadena de seguridad la rechazaria con 401 antes de que Spring MVC llegara a verla.
 * Publicado como bean, Spring Security la atiende el primero y la responde sin pedir
 * credenciales.</p>
 *
 * <p>Lista blanca explicita y restrictiva (OWASP API8): solo los origenes que se listen
 * en {@code gastos.cors.allowed-origins}. Nunca comodin, y sin credenciales, porque el
 * token viaja en una cabecera y no en cookies.</p>
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        if (allowedOrigins.isEmpty()) {
            // Sin origenes autorizados no se registra nada: ninguna peticion de otro
            // origen recibira cabeceras CORS.
            return origen;
        }

        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(allowedOrigins);
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(3600L);

        origen.registerCorsConfiguration("/api/**", configuracion);
        return origen;
    }
}
