package com.gastos.expenses.domain.model;

/**
 * Categorias de gasto del hogar.
 *
 * <p>El flag {@code recurringCommitment} marca los gastos que un banco trata como
 * deuda o compromiso estable al estudiar una hipoteca. El analisis de endeudamiento
 * del contexto de hipoteca lo usa para separar el DTI vivienda del DTI total.</p>
 */
public enum ExpenseCategory {

    ALIMENTACION("Alimentacion", false),
    VIVIENDA("Vivienda", true),
    SUMINISTROS("Suministros", true),
    COCHE("Coche", true),
    TRANSPORTE("Transporte", false),
    SALUD("Salud", false),
    OCIO("Ocio", false),
    SEGUROS("Seguros", true),
    PRESTAMOS("Prestamos", true),
    AHORRO("Ahorro", false),
    OTROS("Otros", false);

    private final String displayName;
    private final boolean recurringCommitment;

    ExpenseCategory(String displayName, boolean recurringCommitment) {
        this.displayName = displayName;
        this.recurringCommitment = recurringCommitment;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isRecurringCommitment() {
        return recurringCommitment;
    }
}
