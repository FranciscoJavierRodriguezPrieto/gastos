package com.gastos.mortgage.domain.model;

/** Semaforo de viabilidad que se muestra en la herramienta de hipoteca. */
public enum ViabilityVerdict {

    /** Algun limite duro se incumple: la operacion no se sostiene. */
    INVIABLE("Inviable"),

    /** Dentro de limites, pero sin margen ante subidas de tipos o imprevistos. */
    VIABLE_AJUSTADA("Viable pero ajustada"),

    /** Holgada en cuota, endeudamiento y colchon de ahorro. */
    OPTIMA("Optima");

    private final String displayName;

    ViabilityVerdict(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
