package com.gastos.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Porcentaje expresado en unidades de "tanto por ciento" (6.00 == 6%).
 * {@link #asRate()} devuelve el tanto por uno (0.06) para los calculos.
 */
public record Percentage(BigDecimal value) implements Comparable<Percentage> {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final int SCALE = 4;

    public Percentage {
        Guard.notNull(value, "value");
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Percentage of(String value) {
        return new Percentage(new BigDecimal(Guard.notBlank(value, "value")));
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage zero() {
        return new Percentage(BigDecimal.ZERO);
    }

    /** Construye el porcentaje a partir de un tanto por uno (0.06 -> 6%). */
    public static Percentage fromRate(BigDecimal rate) {
        return new Percentage(Guard.notNull(rate, "rate").multiply(HUNDRED));
    }

    /** Tanto por uno: 6% -> 0.06. */
    public BigDecimal asRate() {
        return value.divide(HUNDRED, Money.CALCULATION_CONTEXT);
    }

    /** Tasa mensual equivalente por division simple, criterio usado por la banca espanola (TIN/12). */
    public BigDecimal asMonthlyRate() {
        return asRate().divide(BigDecimal.valueOf(12), Money.CALCULATION_CONTEXT);
    }

    public Percentage plus(Percentage other) {
        return new Percentage(value.add(Guard.notNull(other, "other").value));
    }

    public boolean isGreaterThan(Percentage other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThanOrEqualTo(Percentage other) {
        return compareTo(other) <= 0;
    }

    @Override
    public int compareTo(Percentage other) {
        return value.compareTo(Guard.notNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.stripTrailingZeros().toPlainString() + "%";
    }
}
