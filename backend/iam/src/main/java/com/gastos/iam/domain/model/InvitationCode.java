package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import java.util.Locale;

/**
 * Codigo de invitacion al hogar, normalizado y validado en el dominio.
 *
 * <p>A diferencia del token de restablecimiento, que viaja dentro de un enlace y nadie
 * lee, este codigo esta pensado para <strong>dictarse o teclearse</strong>: se manda por
 * WhatsApp, se lee en voz alta o se copia a mano. Eso obliga a dos cosas.</p>
 *
 * <p><strong>Alfabeto de Crockford</strong> ({@value #ALPHABET}): 32 simbolos sin las
 * cuatro letras que se confunden al leer —I, L, O y U—. Las tres primeras se corrigen
 * solas al normalizar (I y L valen 1, O vale 0); la U se excluye ademas porque su
 * ausencia evita que salgan palabras malsonantes por azar.</p>
 *
 * <p><strong>Doce simbolos</strong> = 60 bits de entropia. Es mucho mas corto que los
 * 256 bits del token de restablecimiento, y a proposito: aquello no se teclea y esto si.
 * Sesenta bits siguen estando fuera del alcance de la fuerza bruta contra una API con
 * limite de peticiones, y la invitacion caduca en dias y es de un solo uso.</p>
 *
 * <p>Se guarda y se compara siempre la forma normalizada (sin guiones); los guiones son
 * solo presentacion, para que el codigo se lea de un vistazo.</p>
 */
public record InvitationCode(String value) {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LENGTH = 12;
    private static final int GROUP = 4;

    public InvitationCode {
        Guard.notBlank(value, "codigo");
        value = normalize(value);
        if (value.length() != LENGTH) {
            throw new DomainException("El codigo de invitacion tiene " + LENGTH + " caracteres");
        }
        for (char c : value.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                throw new DomainException("El codigo de invitacion contiene caracteres que no son "
                        + "validos");
            }
        }
    }

    /**
     * Deja el codigo en su forma canonica: mayusculas, sin separadores y con las letras
     * ambiguas traducidas al digito que representan.
     *
     * <p>Que la correccion ocurra aqui y no en el formulario es deliberado: quien teclee
     * {@code O} donde hay un cero acierta venga de donde venga la peticion.</p>
     */
    private static String normalize(String raw) {
        StringBuilder limpio = new StringBuilder(LENGTH);
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            switch (c) {
                case 'O' -> limpio.append('0');
                case 'I', 'L' -> limpio.append('1');
                default -> {
                    if (Character.isLetterOrDigit(c)) {
                        limpio.append(c);
                    }
                    // Guiones, espacios y demas separadores se descartan en silencio:
                    // son adorno, y quien copia y pega suele arrastrarlos.
                }
            }
        }
        return limpio.toString();
    }

    /** Forma legible, en grupos de cuatro: {@code A1B2-C3D4-E5F6}. */
    public String formatted() {
        StringBuilder salida = new StringBuilder(LENGTH + LENGTH / GROUP);
        for (int i = 0; i < value.length(); i++) {
            if (i > 0 && i % GROUP == 0) {
                salida.append('-');
            }
            salida.append(value.charAt(i));
        }
        return salida.toString();
    }

    @Override
    public String toString() {
        // Nunca el codigo entero: esto acaba en registros y en trazas de error.
        return "InvitationCode[****]";
    }
}
