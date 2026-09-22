package com.gastos.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitador de peticiones por ventana fija.
 *
 * <p>Mitiga OWASP API4 (consumo de recursos sin restriccion). El simulador se invoca en
 * cada movimiento de un deslizador, asi que un cliente con un bucle mal escrito puede
 * saturar el proceso sin mala intencion; en una instancia gratuita de 512 MB eso es una
 * caida.</p>
 *
 * <p>El contador vive en memoria y por instancia: suficiente para un hogar de dos
 * personas sobre un despliegue de un solo nodo. Si algun dia hay mas de una instancia,
 * hara falta un contador compartido.</p>
 *
 * <p>Se registra <strong>dentro</strong> de la cadena de seguridad y despues de la
 * autenticacion. Colocado antes, el token todavia no estaria verificado y el limite solo
 * podria aplicarse por direccion IP, que es mucho mas facil de rotar.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (exceedsLimit(clientKey(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(properties.windowSeconds()));
            response.getWriter().write("""
                    {"status":429,"error":"TOO_MANY_REQUESTS",\
                    "message":"Demasiadas peticiones, intentelo de nuevo en unos segundos"}""");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean exceedsLimit(String key) {
        long currentWindow = clock.millis() / (properties.windowSeconds() * 1000L);
        Window window = windows.compute(key, (ignored, existing) ->
                existing == null || existing.index != currentWindow
                        ? new Window(currentWindow)
                        : existing);
        return window.counter.incrementAndGet() > properties.maxRequests();
    }

    /**
     * Identifica al cliente por el sujeto del token y, si aun no se ha autenticado, por
     * direccion de origen.
     *
     * <p>Se toma del {@code SecurityContext} y nunca de una cabecera: una cabecera la
     * controla el cliente, y bastaria rotar su valor para esquivar el limite. El token,
     * en cambio, va firmado.</p>
     */
    private static String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            return "u:" + jwt.getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static final class Window {
        private final long index;
        private final AtomicInteger counter = new AtomicInteger();

        private Window(long index) {
            this.index = index;
        }
    }
}
