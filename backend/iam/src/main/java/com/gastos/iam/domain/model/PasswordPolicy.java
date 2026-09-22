package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;

/**
 * Requisitos minimos de una contrasena.
 *
 * <p>Se exige longitud y no composicion (mayusculas, digitos, simbolos). Es lo que
 * recomiendan las guias actuales: las reglas de composicion empujan a la gente hacia
 * patrones predecibles del tipo {@code Verano2026!}, mientras que la longitud es lo que
 * de verdad encarece un ataque por fuerza bruta.</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(char[] rawPassword) {
        if (rawPassword == null || rawPassword.length == 0) {
            throw new DomainException("La contrasena es obligatoria");
        }
        if (rawPassword.length < MIN_LENGTH) {
            throw new DomainException("La contrasena debe tener al menos " + MIN_LENGTH
                    + " caracteres");
        }
        if (rawPassword.length > MAX_LENGTH) {
            // Un limite superior tambien importa: sin el, una contrasena enorme convierte
            // cada intento de login en trabajo de CPU gratis para quien ataca.
            throw new DomainException("La contrasena no puede superar los " + MAX_LENGTH
                    + " caracteres");
        }
    }
}
