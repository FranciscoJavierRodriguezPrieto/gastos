package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;

/**
 * Perfil economico y familiar del hogar solicitante.
 *
 * @param netMonthlyIncome   ingresos netos mensuales agregados de los dos convivientes
 * @param otherMonthlyDebts  cuotas estables ya comprometidas (prestamo del coche, etc.)
 * @param age                edad del solicitante de MAS edad. Los programas exigen que
 *                           todas las personas adquirentes cumplan el limite, asi que el que
 *                           decide es el mayor de los dos, no el mas joven
 * @param firstHome          si es la primera vivienda en propiedad
 * @param familyWithChildren familia con hijos menores a su cargo, numerosa o monoparental.
 *                           Mi Primera Vivienda la admite al 100% sin limite de edad
 * @param largeFamily        tiene titulo oficial de familia numerosa: da derecho al tipo
 *                           reducido del 4% en el ITP de Madrid
 * @param primaryResidence   la vivienda sera la residencia habitual. Es condicion tanto de
 *                           la rebaja del ITP como de cualquier programa de primera vivienda
 */
public record ApplicantProfile(Money netMonthlyIncome,
                               Money otherMonthlyDebts,
                               int age,
                               boolean firstHome,
                               boolean familyWithChildren,
                               boolean largeFamily,
                               boolean primaryResidence) {

    /**
     * Perfil sin circunstancias familiares y para residencia habitual, que es el caso
     * normal de quien usa un simulador de primera vivienda.
     */
    public ApplicantProfile(Money netMonthlyIncome, Money otherMonthlyDebts, int age,
                            boolean firstHome) {
        this(netMonthlyIncome, otherMonthlyDebts, age, firstHome, false, false, true);
    }

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

    /** Mismo perfil con otros ingresos: lo usa el barrido de sensibilidad. */
    public ApplicantProfile withNetMonthlyIncome(Money newIncome) {
        return new ApplicantProfile(newIncome, otherMonthlyDebts, age, firstHome,
                familyWithChildren, largeFamily, primaryResidence);
    }
}
