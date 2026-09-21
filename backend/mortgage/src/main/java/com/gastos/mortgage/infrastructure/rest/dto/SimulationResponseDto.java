package com.gastos.mortgage.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
                                    FinancingDecisionDto financingDecision,
                                    ViabilityDto viability) {

    /**
     * Como se llego al LTV aplicado.
     *
     * @param evaluations estado de cada programa del catalogo frente a este escenario,
     *                    para que la pantalla pueda mostrar "cumples estos dos y del
     *                    tercero te falta la edad"
     * @param notes       explicaciones en texto de por que salio este LTV
     */
    public record FinancingDecisionDto(String mode,
                                       String modeLabel,
                                       BigDecimal appliedLoanToValue,
                                       UUID appliedProgramId,
                                       String appliedProgramName,
                                       List<ProgramEligibilityDto> evaluations,
                                       List<String> notes) {
    }

    /** Resultado de contrastar el escenario con un programa concreto. */
    public record ProgramEligibilityDto(UUID programId,
                                        String programName,
                                        BigDecimal maxLoanToValue,
                                        boolean active,
                                        boolean eligible,
                                        List<String> unmetCriteria) {
    }

    /**
     * Gastos no financiables de la compraventa.
     *
     * @param transferTaxRate  tipo de ITP aplicado, ya con reducciones
     * @param transferTaxBasis por que ese tipo, en palabras
     */
    public record UpfrontCostsDto(BigDecimal transferTax,
                                  BigDecimal ancillaryCosts,
                                  BigDecimal total,
                                  BigDecimal transferTaxRate,
                                  String transferTaxBasis) {
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
