package com.gastos.mortgage.domain.model;

import com.gastos.mortgage.domain.policy.PurchaseCostsPolicy;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;

/**
 * Gastos iniciales NO financiables de la compraventa.
 *
 * <p>Es la partida que hunde la mayoria de operaciones de primera vivienda: aunque
 * el banco financie el 100% del precio, impuestos y gastos hay que aportarlos en
 * efectivo el dia de la firma.</p>
 *
 * @param transferTaxRate  tipo de ITP aplicado de verdad, ya con reducciones
 * @param transferTaxBasis por que ese tipo; se ensena para poder contrastarlo
 */
public record UpfrontCosts(Money propertyPrice,
                           Money transferTax,
                           Money ancillaryCosts,
                           Money total,
                           Percentage transferTaxRate,
                           String transferTaxBasis) {

    public UpfrontCosts {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(transferTax, "transferTax");
        Guard.notNull(ancillaryCosts, "ancillaryCosts");
        Guard.notNull(total, "total");
        Guard.notNull(transferTaxRate, "transferTaxRate");
        Guard.notBlank(transferTaxBasis, "transferTaxBasis");
    }

    /** Con las circunstancias del comprador: es lo que usa el simulador. */
    public static UpfrontCosts of(Money propertyPrice, PurchaseCostsPolicy policy,
                                  ApplicantProfile applicant) {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(policy, "policy");

        PurchaseCostsPolicy.TransferTax itp = policy.transferTaxFor(propertyPrice, applicant);
        Money transferTax = propertyPrice.percentageOf(itp.rate());
        Money ancillary = propertyPrice.percentageOf(policy.ancillaryCostsRate());
        return new UpfrontCosts(propertyPrice, transferTax, ancillary, transferTax.plus(ancillary),
                itp.rate(), itp.basis());
    }

    /** Sin circunstancias personales: tipo general, el peor caso. */
    public static UpfrontCosts of(Money propertyPrice, PurchaseCostsPolicy policy) {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(policy, "policy");

        Money transferTax = propertyPrice.percentageOf(policy.transferTaxRate());
        Money ancillary = propertyPrice.percentageOf(policy.ancillaryCostsRate());
        return new UpfrontCosts(propertyPrice, transferTax, ancillary, transferTax.plus(ancillary),
                policy.transferTaxRate(), "Tipo general del " + policy.transferTaxRate());
    }
}
