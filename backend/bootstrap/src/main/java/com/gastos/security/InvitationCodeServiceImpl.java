package com.gastos.security;

import com.gastos.iam.application.port.InvitationCodeService;
import com.gastos.iam.domain.model.InvitationCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Codigos de invitacion: 12 simbolos del alfabeto de Crockford, guardados como SHA-256.
 *
 * <p>SHA-256 y no BCrypt, por el mismo motivo que con los tokens de refresco y de
 * restablecimiento: es un valor aleatorio, no una contrasena elegida por una persona, asi
 * que no hay diccionario contra el que defenderse y un hash lento solo anadiria latencia.
 * Los 60 bits de entropia hacen el trabajo que en una contrasena hace el coste del
 * hash.</p>
 */
@Service
public class InvitationCodeServiceImpl implements InvitationCodeService {

    private final InvitationProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationCodeServiceImpl(InvitationProperties properties) {
        this.properties = properties;
    }

    /**
     * Genera el codigo simbolo a simbolo.
     *
     * <p>El alfabeto tiene 32 simbolos, potencia de dos, asi que
     * {@code nextInt(32)} reparte uniformemente y no hace falta el descarte que
     * necesitaria un alfabeto de tamano cualquiera.</p>
     */
    @Override
    public InvitationCode newCode() {
        StringBuilder codigo = new StringBuilder(InvitationCode.LENGTH);
        for (int i = 0; i < InvitationCode.LENGTH; i++) {
            codigo.append(InvitationCode.ALPHABET.charAt(
                    secureRandom.nextInt(InvitationCode.ALPHABET.length())));
        }
        return new InvitationCode(codigo.toString());
    }

    @Override
    public String hashCode(InvitationCode code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Sobre el valor normalizado, no sobre lo que tecleo nadie: asi el mismo
            // codigo con guiones, en minusculas o con una O por un cero da el mismo hash.
            return HexFormat.of().formatHex(
                    digest.digest(code.value().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    @Override
    public Duration invitationTtl() {
        return properties.ttl();
    }

    /**
     * El codigo va en el fragmento y no en la query, igual que el token del correo: lo
     * que va detras de la almohadilla no se envia al servidor ni aparece en ningun
     * registro de acceso, y ademas la aplicacion navega por fragmento.
     */
    @Override
    public String joinUrl(InvitationCode code) {
        return properties.baseUrl() + "/#/unirse?codigo=" + code.formatted();
    }
}
