package com.gastos.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeceras de seguridad en todas las respuestas de la API.
 *
 * <p>Mitiga OWASP API8 (configuracion incorrecta). Aunque la API solo devuelve JSON,
 * las cabeceras siguen importando: un navegador que interprete una respuesta como HTML
 * por adivinacion de tipo convierte un JSON reflejado en un XSS.</p>
 *
 * <p>Cuando llegue Spring Security en {@code feature/security-jwt-passkeys}, estas
 * cabeceras pasaran a configurarse alli y este filtro desaparecera.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Nada de adivinar el tipo de contenido: si dice JSON, es JSON.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // La API nunca se muestra dentro de un marco.
        response.setHeader("X-Frame-Options", "DENY");
        // Una respuesta JSON no necesita cargar absolutamente nada.
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        // Datos economicos del hogar: fuera de cualquier cache intermedia.
        response.setHeader("Cache-Control", "no-store");
        // HSTS solo tiene sentido sobre TLS; en local se omite para no bloquear el navegador.
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        chain.doFilter(request, response);
    }
}
