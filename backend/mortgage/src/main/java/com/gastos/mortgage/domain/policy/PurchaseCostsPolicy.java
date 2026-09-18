package com.gastos.mortgage.domain.policy;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Percentage;

/**
 * Tipos impositivos y gastos de compraventa aplicables. Son parametros de negocio,
 * no constantes de codigo: la normativa autonomica cambia y el simulador debe poder
 * ajustarse sin recompilar.
 *
 * <p>Los valores por defecto corresponden a vivienda de segunda mano en la Comunidad
 * de Madrid: ITP del 6% mas un 4% estimado de notaria, registro y gestoria. Para obra
 * nueva habria que sustituir el ITP por IVA (10%) mas AJD.</p>
 *
 * <p><strong>Aviso:</strong> los porcentajes por defecto deben contrastarse con la
 * normativa vigente publicada en el BOCM antes de tomar decisiones reales.</p>
 */
public record PurchaseCostsPolicy(Percentage transferTaxRate, Percentage ancillaryCostsRate) {

    public PurchaseCostsPolicy {
        Guard.notNull(transferTaxRate, "transferTaxRate");
        Guard.notNull(ancillaryCostsRate, "ancillaryCostsRate");
    }

    /** ITP 6% + 4% de notaria, registro y gestoria: el 10% no financiable. */
    public static PurchaseCostsPolicy madridSecondHand() {
        return new PurchaseCostsPolicy(Percentage.of("6.00"), Percentage.of("4.00"));
    }

    /**
     * Variante con la bonificacion autonomica del ITP para menores de 40 anos en
     * vivienda habitual (reduccion del 10% sobre la cuota: 6% -> 5.4%).
     */
    public static PurchaseCostsPolicy madridSecondHandYoungBuyer() {
        return new PurchaseCostsPolicy(Percentage.of("5.40"), Percentage.of("4.00"));
    }

    public Percentage totalRate() {
        return transferTaxRate.plus(ancillaryCostsRate);
    }
}
