package com.gastos.mortgage.domain.policy;

import com.gastos.mortgage.domain.model.ApplicantProfile;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Impuestos y gastos de la compraventa.
 *
 * <p>Son parametros de negocio, no constantes: se cargan de {@code application.yml} y la
 * normativa autonomica cambia sin avisar. Los valores por defecto son los de vivienda de
 * segunda mano en la Comunidad de Madrid.</p>
 *
 * <p>El ITP de Madrid tiene tres escalones, y el orden importa porque son
 * incompatibles entre si:</p>
 *
 * <ol>
 *   <li><strong>4%</strong> para familia numerosa que compra su vivienda habitual.</li>
 *   <li><strong>6% con bonificacion del 10% de la cuota</strong> —un 5,4% efectivo— para
 *       cualquier vivienda habitual de hasta 250.000 €. Sin limite de edad: la
 *       bonificacion es de la vivienda, no del comprador.</li>
 *   <li><strong>6%</strong> en el resto de casos.</li>
 * </ol>
 *
 * <p>Queda fuera la bonificacion del 100% para menores de 35 en municipios de menos de
 * 2.500 habitantes: necesitaria saber el municipio, y es un caso muy minoritario.</p>
 *
 * <p>El limite de 250.000 € se aplica sobre el precio, aunque la norma lo aplica al mayor
 * entre precio y valor de referencia catastral. Si el valor de referencia supera al
 * precio, el resultado real puede ser peor que el simulado.</p>
 *
 * @param habitualResidenceRebate      porcentaje de la cuota que se bonifica; nulo si no hay
 * @param habitualResidenceRebateLimit precio maximo para la bonificacion
 * @param largeFamilyRate              tipo reducido para familia numerosa; nulo si no hay
 */
public record PurchaseCostsPolicy(Percentage transferTaxRate,
                                  Percentage ancillaryCostsRate,
                                  Percentage habitualResidenceRebate,
                                  Money habitualResidenceRebateLimit,
                                  Percentage largeFamilyRate) {

    private static final Percentage HUNDRED = Percentage.of("100.00");

    /** Politica sin bonificaciones: el tipo general para todo el mundo. */
    public PurchaseCostsPolicy(Percentage transferTaxRate, Percentage ancillaryCostsRate) {
        this(transferTaxRate, ancillaryCostsRate, null, null, null);
    }

    public PurchaseCostsPolicy {
        Guard.notNull(transferTaxRate, "transferTaxRate");
        Guard.notNull(ancillaryCostsRate, "ancillaryCostsRate");
        if ((habitualResidenceRebate == null) != (habitualResidenceRebateLimit == null)) {
            throw new DomainException("La bonificacion por vivienda habitual necesita a la vez el "
                    + "porcentaje y el precio maximo");
        }
        if (habitualResidenceRebate != null && habitualResidenceRebate.isGreaterThan(HUNDRED)) {
            throw new DomainException("La bonificacion no puede superar el 100% de la cuota");
        }
    }

    /**
     * Madrid, segunda mano, normativa vigente: ITP 6% con bonificacion del 10% hasta
     * 250.000 € en vivienda habitual, 4% para familia numerosa, y un 4% estimado de notaria,
     * registro y gestoria.
     */
    public static PurchaseCostsPolicy madridSecondHand() {
        return new PurchaseCostsPolicy(
                Percentage.of("6.00"),
                Percentage.of("4.00"),
                Percentage.of("10.00"),
                Money.euros(250_000),
                Percentage.of("4.00"));
    }

    /** El tipo que corresponde a esta compra, con el motivo en palabras. */
    public TransferTax transferTaxFor(Money propertyPrice, ApplicantProfile applicant) {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(applicant, "applicant");

        if (!applicant.primaryResidence()) {
            return new TransferTax(transferTaxRate, "Tipo general del " + transferTaxRate
                    + ": las reducciones solo se aplican a la vivienda habitual");
        }

        // Familia numerosa primero: es el tipo mas bajo y es incompatible con la
        // bonificacion del 10%, asi que nunca conviene lo otro.
        if (applicant.largeFamily() && largeFamilyRate != null) {
            return new TransferTax(largeFamilyRate, "Tipo reducido del " + largeFamilyRate
                    + " por familia numerosa en vivienda habitual");
        }

        if (habitualResidenceRebate != null) {
            if (!propertyPrice.isGreaterThan(habitualResidenceRebateLimit)) {
                return new TransferTax(rebated(), "Tipo del " + transferTaxRate
                        + " con bonificacion del " + habitualResidenceRebate + " de la cuota por "
                        + "vivienda habitual de hasta " + habitualResidenceRebateLimit);
            }
            return new TransferTax(transferTaxRate, "Tipo general del " + transferTaxRate
                    + ": la bonificacion por vivienda habitual solo llega hasta "
                    + habitualResidenceRebateLimit);
        }

        return new TransferTax(transferTaxRate, "Tipo general del " + transferTaxRate);
    }

    /** 6% x (1 - 10%) = 5,40%. */
    private Percentage rebated() {
        BigDecimal factor = HUNDRED.value().subtract(habitualResidenceRebate.value())
                .divide(HUNDRED.value(), 8, RoundingMode.HALF_UP);
        return Percentage.of(transferTaxRate.value().multiply(factor));
    }

    /** Tipo general mas gastos: el peor caso, sin circunstancias personales. */
    public Percentage totalRate() {
        return transferTaxRate.plus(ancillaryCostsRate);
    }

    /**
     * Tipo de ITP aplicado a una compra concreta.
     *
     * @param basis por que ese tipo y no otro. Se ensena en pantalla: una cifra de impuestos
     *              sin explicacion no se puede contrastar con el notario
     */
    public record TransferTax(Percentage rate, String basis) {
        public TransferTax {
            Guard.notNull(rate, "rate");
            Guard.notBlank(basis, "basis");
        }
    }
}
