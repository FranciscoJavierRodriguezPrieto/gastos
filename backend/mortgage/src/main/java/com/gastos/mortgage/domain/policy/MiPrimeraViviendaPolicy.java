package com.gastos.mortgage.domain.policy;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/**
 * Parametros del programa "Mi Primera Vivienda" de la Comunidad de Madrid: un aval
 * publico sobre una parte del prestamo que permite a la entidad elevar el LTV muy por
 * encima del 80% habitual, sin que ello cubra los gastos de compraventa.
 *
 * <p>Se modela como politica configurable y no como constantes: las condiciones del
 * programa (limite de precio, edad, LTV avalado) se revisan por convocatoria.</p>
 *
 * <p><strong>Aviso:</strong> los valores por defecto son una aproximacion de trabajo y
 * deben verificarse contra la convocatoria vigente publicada por la Comunidad de
 * Madrid antes de usarse para decidir una compra.</p>
 *
 * @param maxLoanToValue    LTV maximo alcanzable con el aval
 * @param maxPropertyPrice  precio maximo del inmueble admitido en el programa
 * @param maxApplicantAge   edad maxima del solicitante
 * @param requiresFirstHome si exige que sea la primera vivienda en propiedad
 */
public record MiPrimeraViviendaPolicy(Percentage maxLoanToValue,
                                      Money maxPropertyPrice,
                                      int maxApplicantAge,
                                      boolean requiresFirstHome) {

    public MiPrimeraViviendaPolicy {
        Guard.notNull(maxLoanToValue, "maxLoanToValue");
        Guard.notNull(maxPropertyPrice, "maxPropertyPrice");
        Guard.positive(maxApplicantAge, "maxApplicantAge");
    }

    /** Configuracion de referencia del programa autonomico. */
    public static MiPrimeraViviendaPolicy defaults() {
        return new MiPrimeraViviendaPolicy(
                Percentage.of("95.00"),
                Money.euros(390_000),
                35,
                true);
    }

    /** Financiacion estandar sin aval publico: el clasico 80% de tasacion. */
    public static MiPrimeraViviendaPolicy withoutProgram() {
        return new MiPrimeraViviendaPolicy(
                Percentage.of("80.00"),
                Money.euros(1_000_000_000L),
                200,
                false);
    }

    /**
     * Comprueba si la operacion encaja en el programa. Devuelve el LTV maximo
     * aplicable: el del programa si cumple, o el estandar del 80% si no.
     */
    public Percentage applicableLoanToValue(Money propertyPrice, int applicantAge, boolean firstHome) {
        Guard.notNull(propertyPrice, "propertyPrice");
        boolean eligible = !propertyPrice.isGreaterThan(maxPropertyPrice)
                && applicantAge <= maxApplicantAge
                && (!requiresFirstHome || firstHome);
        return eligible ? maxLoanToValue : Percentage.of("80.00");
    }
}
