package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Percentage;
import java.util.UUID;

/**
 * Eleccion del usuario sobre como determinar el LTV maximo.
 *
 * <p>Las combinaciones imposibles se rechazan en el constructor: no existe un
 * {@code FinancingChoice} en modo PROGRAMA sin programa, ni en modo MANUAL sin LTV. El
 * resto del codigo puede confiar en ello sin volver a comprobarlo.</p>
 */
public record FinancingChoice(FinancingMode mode, UUID programId, Percentage manualLoanToValue) {

    private static final Percentage MAX_MANUAL_LTV = Percentage.of("100.00");

    public FinancingChoice {
        if (mode == null) {
            throw new DomainException("El modo de financiacion es obligatorio");
        }
        switch (mode) {
            case PROGRAMA -> {
                if (programId == null) {
                    throw new DomainException("El modo PROGRAMA exige indicar que programa aplicar");
                }
                manualLoanToValue = null;
            }
            case MANUAL -> {
                if (manualLoanToValue == null) {
                    throw new DomainException("El modo MANUAL exige indicar el LTV");
                }
                if (manualLoanToValue.value().signum() <= 0
                        || manualLoanToValue.isGreaterThan(MAX_MANUAL_LTV)) {
                    throw new DomainException("El LTV manual debe estar entre 0 y 100, recibido: "
                            + manualLoanToValue);
                }
                programId = null;
            }
            case AUTOMATICO -> {
                programId = null;
                manualLoanToValue = null;
            }
        }
    }

    public static FinancingChoice automatic() {
        return new FinancingChoice(FinancingMode.AUTOMATICO, null, null);
    }

    public static FinancingChoice program(UUID programId) {
        return new FinancingChoice(FinancingMode.PROGRAMA, programId, null);
    }

    public static FinancingChoice manual(Percentage loanToValue) {
        return new FinancingChoice(FinancingMode.MANUAL, null, loanToValue);
    }
}
