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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String HOUSEHOLD_HEADER = "X-Household-Id";

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
     * Identifica al cliente por hogar y, si aun no se conoce, por direccion de origen.
     *
     * <p>No se usa {@code X-Forwarded-For} sin mas: es una cabecera que el cliente
     * controla, y confiar en ella permitiria esquivar el limite rotando su valor.</p>
     */
    private static String clientKey(HttpServletRequest request) {
        String household = request.getHeader(HOUSEHOLD_HEADER);
        return household != null && !household.isBlank() ? "h:" + household : "ip:" + request.getRemoteAddr();
    }

    private static final class Window {
        private final long index;
        private final AtomicInteger counter = new AtomicInteger();

        private Window(long index) {
            this.index = index;
        }
    }
}
