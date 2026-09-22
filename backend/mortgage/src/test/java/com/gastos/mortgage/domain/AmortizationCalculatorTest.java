package com.gastos.mortgage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gastos.mortgage.domain.model.AmortizationSchedule;
import com.gastos.mortgage.domain.model.Installment;
import com.gastos.mortgage.domain.model.LoanTerms;
import com.gastos.mortgage.domain.service.AmortizationCalculator;
import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.math.BigDecimal;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Amortizacion francesa")
class AmortizationCalculatorTest {

    private static final Offset<Double> ONE_CENT = Offset.offset(0.02);

    @ParameterizedTest(name = "{0} EUR al {1}% a {2} anos -> cuota {3} EUR")
    @CsvSource({
            "200000, 3.00, 30, 843.21",
            "150000, 2.00, 20, 758.82",
            "100000, 5.00, 30, 536.82"
    })
    @DisplayName("reproduce la cuota de referencia del mercado")
    void computesMarketReferencePayment(long principal, String rate, int years, double expected) {
        LoanTerms terms = new LoanTerms(Money.euros(principal), Percentage.of(rate), years);

        Money payment = AmortizationCalculator.monthlyPayment(terms);

        assertThat(payment.amount().doubleValue()).isCloseTo(expected, ONE_CENT);
    }

    @Test
    @DisplayName("con tipo cero reparte el capital a partes iguales")
    void handlesZeroInterest() {
        LoanTerms terms = new LoanTerms(Money.euros(120_000), Percentage.zero(), 20);

        assertThat(AmortizationCalculator.monthlyPayment(terms).amount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("el cuadro cierra con capital pendiente cero")
    void scheduleEndsAtZeroBalance() {
        LoanTerms terms = new LoanTerms(Money.euros(200_000), Percentage.of("3.00"), 30);

        AmortizationSchedule schedule = AmortizationCalculator.schedule(terms);

        assertThat(schedule.installments()).hasSize(360);
        assertThat(schedule.installment(360).outstandingBalance()).isEqualTo(Money.zero());
    }

    @Test
    @DisplayName("la suma del capital amortizado equivale al principal")
    void principalPaidSumsToPrincipal() {
        LoanTerms terms = new LoanTerms(Money.euros(150_000), Percentage.of("2.50"), 25);

        Money totalPrincipal = AmortizationCalculator.schedule(terms).installments().stream()
                .map(Installment::principalPaid)
                .reduce(Money.zero(), Money::plus);

        assertThat(totalPrincipal).isEqualTo(Money.euros(150_000));
    }

    @Test
    @DisplayName("los intereses decrecen a lo largo de la vida del prestamo")
    void interestDecreasesOverTime() {
        LoanTerms terms = new LoanTerms(Money.euros(200_000), Percentage.of("3.00"), 30);

        AmortizationSchedule schedule = AmortizationCalculator.schedule(terms);

        assertThat(schedule.installment(1).interest())
                .isGreaterThan(schedule.installment(180).interest());
        assertThat(schedule.installment(180).interest())
                .isGreaterThan(schedule.installment(359).interest());
    }

    @Test
    @DisplayName("el coste total supera al capital por el importe de los intereses")
    void totalRepaidIncludesInterest() {
        LoanTerms terms = new LoanTerms(Money.euros(200_000), Percentage.of("3.00"), 30);

        AmortizationSchedule schedule = AmortizationCalculator.schedule(terms);

        assertThat(schedule.totalRepaid())
                .isEqualTo(terms.principal().plus(schedule.totalInterest()));
        assertThat(schedule.totalInterest().amount().doubleValue())
                .isCloseTo(103_554.90, Offset.offset(200.0));
    }

    @Test
    @DisplayName("rechaza plazos fuera del rango comercializado")
    void rejectsOutOfRangeTerm() {
        assertThatThrownBy(() -> new LoanTerms(Money.euros(100_000), Percentage.of("3.00"), 45))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("termYears");
    }

    @Test
    @DisplayName("rechaza tipos de interes negativos")
    void rejectsNegativeRate() {
        assertThatThrownBy(() -> new LoanTerms(Money.euros(100_000),
                Percentage.of(new BigDecimal("-0.50")), 20))
                .isInstanceOf(DomainException.class);
    }
}
