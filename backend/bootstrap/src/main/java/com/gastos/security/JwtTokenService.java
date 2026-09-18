package com.gastos.security;

import com.gastos.iam.application.port.TokenService;
import com.gastos.shared.domain.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Emision de tokens: JWT firmado para el acceso, cadena aleatoria opaca para el refresco.
 *
 * <p>Por que el token de acceso es un JWT y el de refresco no: el de acceso se valida en
 * cada peticion y conviene que no toque la base de datos, asi que lleva dentro la
 * identidad y va firmado. El de refresco se usa cada varias horas, tiene que poder
 * revocarse al instante y por tanto debe consultarse en base de datos; si fuera un JWT,
 * revocarlo exigiria igualmente una lista negra, con lo que se gana nada y se complica
 * todo.</p>
 *
 * <p>El de refresco se guarda como SHA-256 y no con BCrypt: es una cadena aleatoria de
 * 256 bits, no una contrasena elegida por una persona, asi que no hay diccionario que
 * valga y un hash lento solo aportaria latencia en cada refresco.</p>
 */
@Service
public class JwtTokenService implements TokenService {

    private static final String CLAIM_HOUSEHOLD = "hid";
    private static final String CLAIM_ROLE = "role";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public String issueAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl()))
                .subject(user.userId().value().toString())
                .claim(CLAIM_HOUSEHOLD, user.householdId().value().toString())
                .claim(CLAIM_ROLE, user.role())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    @Override
    public String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashRefreshToken(String rawRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, algo esta muy roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    @Override
    public Duration refreshTokenTtl() {
        return properties.refreshTokenTtl();
    }
}
