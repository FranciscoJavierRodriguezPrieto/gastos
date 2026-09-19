package com.gastos.security;

import com.gastos.iam.application.port.ResetTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Tokens de restablecimiento: cadena aleatoria de 256 bits, guardada como SHA-256.
 *
 * <p>SHA-256 y no BCrypt por el mismo motivo que con los tokens de refresco: es un valor
 * aleatorio de 256 bits, no una contrasena elegida por una persona, asi que no hay
 * diccionario posible y un hash lento solo anadiria latencia.</p>
 */
@Service
public class ResetTokenServiceImpl implements ResetTokenService {

    private static final int TOKEN_BYTES = 32;

    private final ResetTokenProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ResetTokenServiceImpl(ResetTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public String newResetToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        // Codificacion segura para URL: el token va dentro de un enlace.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashResetToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    @Override
    public Duration resetTokenTtl() {
        return properties.ttl();
    }

    /**
     * El token va en el fragmento y no en la query.
     *
     * <p>Lo que va detras de la almohadilla no se envia al servidor ni aparece en los
     * registros de acceso, y ademas la aplicacion navega por fragmento.</p>
     */
    @Override
    public String resetUrl(String rawToken) {
        return properties.baseUrl() + "/#/restablecer?token=" + rawToken;
    }
}
