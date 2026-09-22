package com.gastos.mortgage.infrastructure.rest.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entrada del simulador. Cada campo corresponde a un control de la herramienta de
 * hipoteca, de modo que mover un deslizador sea reenviar este mismo objeto.
 *
 * <p>Los rangos del DTO son limites de cordura de la API (evitan peticiones absurdas y
 * consumo innecesario de CPU, OWASP API4); los limites de negocio de verdad viven en el
 * dominio, que es quien rechaza un plazo de 45 anos o unos ingresos de cero.</p>
 *
 * @param targetReserve fondo de emergencia que el hogar no quiere destinar a la
 *                      entrada; cero significa "aporto todo el ahorro"
 * @param otherMonthlyDebts cuotas ya comprometidas (coche, prestamos); puede venir del
 *                          resumen de gastos, campo {@code monthlyCommitments}
 */
public record SimulationRequestDto(

        @NotNull(message = "El precio de la vivienda es obligatorio")
        @DecimalMin(value = "1000.00", message = "El precio de la vivienda es demasiado bajo")
        @DecimalMax(value = "10000000.00", message = "El precio de la vivienda es demasiado alto")
        @Digits(integer = 9, fraction = 2, message = "El precio admite como maximo dos decimales")
        BigDecimal propertyPrice,

        @NotNull(message = "El ahorro disponible es obligatorio")
        @DecimalMin(value = "0.00", message = "El ahorro no puede ser negativo")
        @Digits(integer = 9, fraction = 2, message = "El ahorro admite como maximo dos decimales")
        BigDecimal availableSavings,

        @NotNull(message = "El fondo de emergencia es obligatorio, use 0 si aporta todo el ahorro")
        @DecimalMin(value = "0.00", message = "El fondo de emergencia no puede ser negativo")
        @Digits(integer = 9, fraction = 2, message = "El importe admite como maximo dos decimales")
        BigDecimal targetReserve,

        @NotNull(message = "El tipo de interes es obligatorio")
        @DecimalMin(value = "0.00", message = "El tipo de interes no puede ser negativo")
        @DecimalMax(value = "25.00", message = "El tipo de interes es demasiado alto")
        @Digits(integer = 2, fraction = 4, message = "El tipo admite como maximo cuatro decimales")
        BigDecimal annualNominalRate,

        @NotNull(message = "El plazo es obligatorio")
        @Min(value = 5, message = "El plazo minimo es de 5 anos")
        @Max(value = 40, message = "El plazo maximo es de 40 anos")
        Integer termYears,

        @NotNull(message = "Los ingresos netos del hogar son obligatorios")
        @DecimalMin(value = "0.01", message = "Los ingresos netos deben ser mayores que cero")
        @Digits(integer = 7, fraction = 2, message = "Los ingresos admiten como maximo dos decimales")
        BigDecimal netMonthlyIncome,

        @NotNull(message = "Las otras deudas mensuales son obligatorias, use 0 si no hay")
        @DecimalMin(value = "0.00", message = "Las deudas no pueden ser negativas")
        @Digits(integer = 7, fraction = 2, message = "El importe admite como maximo dos decimales")
        BigDecimal otherMonthlyDebts,

        @NotNull(message = "La edad del solicitante es obligatoria")
        @Min(value = 18, message = "La edad minima es 18")
        @Max(value = 100, message = "La edad maxima es 100")
        Integer applicantAge,

        @NotNull(message = "Indique si es la primera vivienda")
        Boolean firstHome,

        /** Familia numerosa, monoparental o con hijos menores a cargo. Por defecto, no. */
        Boolean familyWithChildren,

        /** Titulo oficial de familia numerosa: ITP al 4% en Madrid. Por defecto, no. */
        Boolean largeFamily,

        /** Sera la vivienda habitual. Por defecto, SI: es el caso de primera vivienda. */
        Boolean primaryResidence,

        /**
         * AUTOMATICO, PROGRAMA o MANUAL. Si se omite se asume AUTOMATICO, que es lo que
         * espera quien solo mueve los deslizadores sin tocar la financiacion.
         */
        String financingMode,

        /** Obligatorio en modo PROGRAMA. */
        UUID programId,

        @DecimalMin(value = "0.01", message = "El LTV manual debe ser mayor que cero")
        @DecimalMax(value = "100.00", message = "El LTV manual no puede superar el 100%")
        @Digits(integer = 3, fraction = 2, message = "El LTV admite como maximo dos decimales")
        BigDecimal manualLoanToValue) {

    /**
     * Coherencia entre el modo y sus datos.
     *
     * <p>El dominio ya rechaza estas combinaciones, pero comprobarlas aqui convierte un
     * 422 generico en un 400 que senala el campo exacto que falta.</p>
     */
    @AssertTrue(message = "financingMode: el modo PROGRAMA exige indicar programId")
    public boolean isProgramIdPresentWhenRequired() {
        return !"PROGRAMA".equalsIgnoreCase(trimmedMode()) || programId != null;
    }

    @AssertTrue(message = "financingMode: el modo MANUAL exige indicar manualLoanToValue")
    public boolean isManualLoanToValuePresentWhenRequired() {
        return !"MANUAL".equalsIgnoreCase(trimmedMode()) || manualLoanToValue != null;
    }

    private String trimmedMode() {
        return financingMode == null ? "" : financingMode.trim();
    }
}
