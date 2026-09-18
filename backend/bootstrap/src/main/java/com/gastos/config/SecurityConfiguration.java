package com.gastos.config;

import com.gastos.iam.domain.port.PasswordHasher;
import com.gastos.security.JwtProperties;
import com.gastos.web.RateLimitFilter;
import com.gastos.web.RateLimitProperties;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Configuracion de seguridad HTTP.
 *
 * <p>Criterio general: <strong>todo exige token salvo lo que explicitamente no</strong>.
 * La lista blanca es corta y esta a la vista; si manana se anade un endpoint y nadie
 * toca este fichero, queda protegido por omision, que es como debe ser.</p>
 *
 * <p>Sin sesion de servidor y sin cookies: el token viaja en {@code Authorization}. Por
 * eso CSRF se desactiva de forma deliberada y no por comodidad — sin cookies no hay
 * peticion que el navegador pueda autenticar sola, que es lo que hace posible el CSRF.</p>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class})
public class SecurityConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/status",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RateLimitProperties rateLimit,
                                           Clock clock) throws Exception {
        http
                // Sin esto la cadena de seguridad ignora la configuracion CORS y
                // rechaza con 401 el sondeo previo del navegador, que llega sin token.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/status").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }))
                // Despues de autenticar: asi el limite se aplica por usuario y no por IP.
                .addFilterAfter(new RateLimitFilter(rateLimit, clock),
                        BearerTokenAuthenticationFilter.class)
                // 401 escueto en vez de redirigir a un formulario de login que no existe:
                // esto es una API, no una aplicacion con sesion.
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(JwtProperties properties) {
        OctetSequenceKey key = new OctetSequenceKey.Builder(
                properties.secret().getBytes(StandardCharsets.UTF_8)).build();
        return new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(
                        properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * BCrypt con coste 12.
     *
     * <p>El coste es deliberadamente alto: encarece un ataque por fuerza bruta contra la
     * base de datos y solo se paga al iniciar sesion, no en cada peticion.</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Implementa el puerto del dominio delegando en Spring Security. */
    @Bean
    public PasswordHasher passwordHasher(PasswordEncoder encoder) {
        return new PasswordHasher() {
            @Override
            public String hash(char[] rawPassword) {
                return encoder.encode(CharSequence.class.cast(new String(rawPassword)));
            }

            @Override
            public boolean matches(char[] rawPassword, String storedHash) {
                return encoder.matches(new String(rawPassword), storedHash);
            }
        };
    }
}
