package com.gastos.iam.domain.model;

/**
 * Roles del hogar. La aplicacion esta disenada para dos usuarios convivientes con
 * los mismos privilegios sobre los datos compartidos; {@code OWNER} es quien creo
 * el hogar y el unico que puede invitar o revocar al otro miembro.
 */
public enum Role {
    OWNER,
    MEMBER
}
