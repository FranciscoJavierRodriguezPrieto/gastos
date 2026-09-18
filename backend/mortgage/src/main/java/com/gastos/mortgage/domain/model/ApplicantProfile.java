package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;

/**
 * Perfil economico del hogar solicitante.
 *
 * @param netMonthlyIncome  ingresos netos mensuales agregados de los dos convivientes
 * @param otherMonthlyDebts cuotas estables ya comprometidas (prestamo del coche, etc.)
 * @param age               edad del solicitante mas joven, que es la que rige el acceso
 *                          a los programas de ayuda por edad
 * @param firstHome         si es la primera vivienda en propiedad
 */
public record ApplicantProfile(Money netMonthlyIncome,
                               Money otherMonthlyDebts,
                               int age,
                               boolean firstHome) {

    public ApplicantProfile {
        Guard.notNull(netMonthlyIncome, "netMonthlyIncome");
        Guard.notNull(otherMonthlyDebts, "otherMonthlyDebts");
        Guard.inRange(age, 18, 100, "age");
        if (!netMonthlyIncome.isPositive()) {
            throw new DomainException("Los ingresos netos mensuales deben ser mayores que cero");
        }
        if (otherMonthlyDebts.isNegative()) {
            throw new DomainException("Las deudas mensuales no pueden ser negativas");
        }
    }
}
