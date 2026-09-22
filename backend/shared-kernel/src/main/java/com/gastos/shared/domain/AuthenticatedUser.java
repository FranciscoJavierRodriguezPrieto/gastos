package com.gastos.shared.domain;

/**
 * Identidad del usuario que hace la peticion, ya verificada.
 *
 * <p>Vive en el kernel compartido porque todos los contextos la necesitan y ninguno debe
 * depender de {@code iam} para obtenerla. Es Java puro: no sabe que existe un JWT ni una
 * cabecera HTTP, solo representa a quien pregunta.</p>
 *
 * <p>Sustituye a las cabeceras {@code X-Household-Id} y {@code X-User-Id} de las ramas
 * anteriores, que eran un andamio: cualquiera podia inventarselas.</p>
 */
public record AuthenticatedUser(UserId userId, HouseholdId householdId, String role) {

    public static final String ROLE_OWNER = "OWNER";

    public AuthenticatedUser {
        Guard.notNull(userId, "userId");
        Guard.notNull(householdId, "householdId");
        Guard.notBlank(role, "role");
    }

    public boolean isOwner() {
        return ROLE_OWNER.equals(role);
    }
}
