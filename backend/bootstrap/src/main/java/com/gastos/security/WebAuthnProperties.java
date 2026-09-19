package com.gastos.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de WebAuthn.
 *
 * <p>Los dos valores que hay que acertar al desplegar:</p>
 *
 * <ul>
 *   <li>{@code rpId} es el <strong>dominio del frontend</strong>, no el de la API. Si la
 *       aplicacion se sirve en {@code gastos.example.com}, el valor es ese o bien
 *       {@code example.com}; poner el dominio de la API hace que el navegador rechace la
 *       ceremonia sin mas explicacion.</li>
 *   <li>{@code origins} son las direcciones completas desde las que se admite la
 *       ceremonia, con esquema y puerto. Fuera de {@code localhost} el navegador exige
 *       HTTPS, asi que en produccion siempre empiezan por {@code https://}.</li>
 * </ul>
 *
 * @param ttl cuanto vale un reto. Cinco minutos y no treinta segundos porque una passkey
 *            puede estar en otro aparato: entre leer el codigo QR, desbloquear el movil y
 *            confirmar se va facilmente un minuto
 */
@ConfigurationProperties(prefix = "gastos.webauthn")
public record WebAuthnProperties(String rpId, String rpName, List<String> origins, Duration ttl) {

    public WebAuthnProperties {
        if (rpId == null || rpId.isBlank()) {
            rpId = "localhost";
        }
        if (rpName == null || rpName.isBlank()) {
            rpName = "Gastos";
        }
        origins = origins == null || origins.isEmpty()
                ? List.of("http://localhost:5173")
                : List.copyOf(origins);
        if (ttl == null) {
            ttl = Duration.ofMinutes(5);
        }
    }
}
