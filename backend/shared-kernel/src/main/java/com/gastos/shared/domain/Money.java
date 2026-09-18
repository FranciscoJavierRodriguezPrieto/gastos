package com.gastos.shared.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Importe monetario inmutable. Nunca se usa {@code double} para dinero: todos los
 * calculos se apoyan en {@link BigDecimal} con escala fija de 2 decimales y
 * redondeo HALF_UP (criterio contable habitual en Espana).
 *
 * <p>Los calculos intermedios de precision extendida (por ejemplo la formula de
 * amortizacion francesa) se realizan con {@link #CALCULATION_CONTEXT} y solo se
 * redondean al construir el resultado final.</p>
 */
public final class Money implements Comparable<Money> {

    public static final Currency EUR = Currency.getInstance("EUR");
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL64;

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = Guard.notNull(amount, "amount").setScale(SCALE, ROUNDING);
        this.currency = Guard.notNull(currency, "currency");
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money euros(BigDecimal amount) {
        return new Money(amount, EUR);
    }

    public static Money euros(String amount) {
        return new Money(new BigDecimal(Guard.notBlank(amount, "amount")), EUR);
    }

    public static Money euros(long amount) {
        return new Money(BigDecimal.valueOf(amount), EUR);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO, EUR);
    }

    public BigDecimal amount() {
        return amount;
    }

    public Currency currency() {
        return currency;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multipliedBy(BigDecimal factor) {
        return new Money(amount.multiply(Guard.notNull(factor, "factor"), CALCULATION_CONTEXT), currency);
    }

    public Money dividedBy(BigDecimal divisor) {
        Guard.notNull(divisor, "divisor");
        if (divisor.signum() == 0) {
            throw new DomainException("Division por cero al operar con importes");
        }
        return new Money(amount.divide(divisor, CALCULATION_CONTEXT), currency);
    }

    /** Aplica un porcentaje sobre el importe (p. ej. el 6% de ITP). */
    public Money percentageOf(Percentage percentage) {
        return multipliedBy(Guard.notNull(percentage, "percentage").asRate());
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    /** Ratio adimensional entre dos importes, con precision extendida (base del DTI). */
    public BigDecimal ratioTo(Money other) {
        requireSameCurrency(other);
        if (other.isZero()) {
            throw new DomainException("No se puede calcular un ratio sobre un importe cero");
        }
        return amount.divide(other.amount, CALCULATION_CONTEXT);
    }

    private void requireSameCurrency(Money other) {
        Guard.notNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new DomainException(
                    "Operacion entre divisas distintas: " + currency + " y " + other.currency);
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return amount.compareTo(other.amount) == 0 && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
