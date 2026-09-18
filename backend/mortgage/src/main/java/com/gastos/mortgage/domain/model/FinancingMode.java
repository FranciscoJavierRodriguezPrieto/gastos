package com.gastos.mortgage.domain.model;

/**
 * Como se decide el LTV maximo de la operacion.
 *
 * <p>El calculo de la cuota, del DTI y del veredicto es <strong>identico en los tres
 * modos</strong>. Lo unico que cambia es cuanto te presta el banco.</p>
 */
public enum FinancingMode {

    /** Se evaluan todos los programas activos y se aplica el mejor que cumplas. */
    AUTOMATICO("Automatico"),

    /** Se aplica un programa concreto elegido por el usuario. */
    PROGRAMA("Programa concreto"),

    /**
     * El usuario fija el LTV a mano y no se comprueba ningun requisito.
     *
     * <p>Es la valvula de escape: sirve para simular una oferta concreta de un banco o
     * una ayuda que todavia no este dada de alta en el catalogo.</p>
     */
    MANUAL("Manual");

    private final String displayName;

    FinancingMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
