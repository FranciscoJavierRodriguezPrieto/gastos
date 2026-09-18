package com.gastos.mortgage.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Salida completa del simulador, con todo lo que la pantalla necesita pintar sin
 * recalcular nada por su cuenta.
 *
 * <p>Los motivos del veredicto viajan como texto ya redactado por el dominio: la
 * interfaz muestra por que una operacion no sale, sin tener que reproducir el
 * razonamiento en JavaScript y arriesgarse a que las dos versiones diverjan.</p>
 */
public record SimulationResponseDto(BigDecimal monthlyPayment,
                                    BigDecimal totalInterest,
                                    BigDecimal totalCostOfOwnership,
                                    BigDecimal cashRequiredAtSigning,
                                    UpfrontCostsDto upfrontCosts,
                                    FinancingPlanDto financing,
                                    ViabilityDto viability) {

    /** Gastos no financiables de la compraventa. */
    public record UpfrontCostsDto(BigDecimal transferTax,
                                  BigDecimal ancillaryCosts,
                                  BigDecimal total) {
    }

    /** Como queda estructurada la operacion. */
    public record FinancingPlanDto(BigDecimal loanAmount,
                                   BigDecimal downPayment,
                                   BigDecimal cashRequired,
                                   BigDecimal savingsBuffer,
                                   BigDecimal maxLoanToValue,
                                   BigDecimal effectiveLoanToValue,
                                   boolean savingsSufficient,
                                   BigDecimal shortfall) {
    }

    /**
     * Veredicto y ratios.
     *
     * @param verdict       INVIABLE, VIABLE_AJUSTADA u OPTIMA
     * @param housingDti    cuota hipotecaria sobre ingresos netos, en porcentaje
     * @param totalDti      cuota mas resto de deudas sobre ingresos netos, en porcentaje
     * @param residualIncome renta disponible tras atender todas las cuotas
     */
    public record ViabilityDto(String verdict,
                               String verdictLabel,
                               boolean viable,
                               BigDecimal housingDti,
                               BigDecimal totalDti,
                               BigDecimal residualIncome,
                               List<String> blockingReasons,
                               List<String> warnings) {
    }
}
