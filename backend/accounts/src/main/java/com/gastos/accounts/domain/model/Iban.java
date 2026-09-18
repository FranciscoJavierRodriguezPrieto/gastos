package com.gastos.accounts.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import java.math.BigInteger;
import java.util.Locale;

/**
 * IBAN validado con el algoritmo mod-97 (ISO 13616).
 *
 * <p>El valor completo se persiste cifrado en reposo y {@link #masked()} es la unica
 * representacion que sale hacia el cliente o hacia las trazas: minimiza el impacto de
 * una fuga y cumple el principio de minimizacion de datos del RGPD.</p>
 */
public record Iban(String value) {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);
    private static final String FORMAT = "^[A-Z]{2}\\d{2}[A-Z0-9]{10,30}$";

    public Iban {
        Guard.notBlank(value, "iban");
        value = value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!value.matches(FORMAT)) {
            throw new DomainException("Formato de IBAN no valido");
        }
        if (!isChecksumValid(value)) {
            throw new DomainException("El digito de control del IBAN no es correcto");
        }
    }

    private static boolean isChecksumValid(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isDigit(c) ? String.valueOf(c) : Character.getNumericValue(c));
        }
        return new BigInteger(numeric.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    /** Representacion segura para UI y trazas: solo se revelan pais y cuatro ultimos digitos. */
    public String masked() {
        String tail = value.substring(value.length() - 4);
        return value.substring(0, 2) + "**************" + tail;
    }

    @Override
    public String toString() {
        return masked();
    }
}
