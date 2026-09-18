package com.gastos.iam.domain.port;

/**
 * Puerto de cifrado de contrasenas.
 *
 * <p>El dominio declara que necesita cifrar y comprobar, sin decir con que. Asi migrar
 * de BCrypt a Argon2 no toca ni una regla de negocio, y los casos de uso se pueden
 * testear con una implementacion trivial en lugar de pagar el coste real del hash en
 * cada test.</p>
 */
public interface PasswordHasher {

    String hash(char[] rawPassword);

    boolean matches(char[] rawPassword, String storedHash);
}
