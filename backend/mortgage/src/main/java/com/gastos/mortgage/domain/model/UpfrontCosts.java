package com.gastos.mortgage.domain.model;

import com.gastos.mortgage.domain.policy.PurchaseCostsPolicy;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;

/**
 * Gastos iniciales NO financiables de la compraventa.
 *
 * <p>Es la partida que hunde la mayoria de operaciones de primera vivienda: aunque
 * el banco financie el 100% del precio, impuestos y gastos hay que aportarlos en
 * efectivo el dia de la firma.</p>
 */
public record UpfrontCosts(Money propertyPrice,
                           Money transferTax,
                           Money ancillaryCosts,
                           Money total) {

    public UpfrontCosts {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(transferTax, "transferTax");
        Guard.notNull(ancillaryCosts, "ancillaryCosts");
        Guard.notNull(total, "total");
    }

    public static UpfrontCosts of(Money propertyPrice, PurchaseCostsPolicy policy) {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(policy, "policy");

        Money transferTax = propertyPrice.percentageOf(policy.transferTaxRate());
        Money ancillary = propertyPrice.percentageOf(policy.ancillaryCostsRate());
        return new UpfrontCosts(propertyPrice, transferTax, ancillary, transferTax.plus(ancillary));
    }
}
