package com.gastos.expenses.infrastructure.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Plan de la prevision de ahorro, tal cual lo edita el formulario.
 *
 * <p>Es a la vez peticion y respuesta: se guarda y se devuelve entero. Los limites de
 * tamano no son decorativos: el plan se guarda como documento y sin ellos cualquiera
 * podria llenar la base de datos con una sola peticion (OWASP API4).</p>
 *
 * <p>Los meses van como {@code yyyy-MM}: la prevision trabaja por meses enteros.</p>
 */
public record SavingsPlanDto(

        @NotNull @Pattern(regexp = MONTH, message = "El mes de inicio tiene formato yyyy-MM")
        String startMonth,

        @NotNull @Size(max = 6, message = "Como mucho 6 personas") @Valid
        List<Person> people,

        @NotNull @Valid
        Joint joint) {

    public static final String MONTH = "[0-9]{4}-(0[1-9]|1[0-2])";

    /** Una persona del hogar y su dinero personal. */
    public record Person(
            @NotBlank @Size(max = 60) String name,
            /** Lo que ya tiene ahorrado al empezar. Puede ser negativo. */
            @NotNull @Digits(integer = 9, fraction = 2) BigDecimal initialBalance,
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal monthlyIncome,
            /** Ingresos extra de cada mes: una ayuda familiar, un alquiler. */
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal extraIncome,
            /** Importe de cada paga extraordinaria. */
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal bonusAmount,
            @NotNull @Size(max = 12) List<@NotNull @Min(1) @Max(12) Integer> bonusMonths,
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal jointContribution,
            @NotNull @Size(max = 20) @Valid List<PersonalExpense> expenses) {
    }

    /** Gasto fijo personal. Sin {@code until}, no termina. */
    public record PersonalExpense(
            @NotBlank @Size(max = 60) String name,
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @Pattern(regexp = MONTH, message = "El mes de fin tiene formato yyyy-MM") String until) {
    }

    /** La cuenta conjunta. Las aportaciones de cada persona salen de {@link Person}. */
    public record Joint(
            @NotNull @Digits(integer = 9, fraction = 2) BigDecimal initialBalance,
            /** Lo que entra de mas cada mes, ademas de las aportaciones. */
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal extraIncome,
            /** Desde que mes entra ese extra. Sin valor, desde el principio. */
            @Pattern(regexp = MONTH, message = "El mes del ingreso extra tiene formato yyyy-MM")
            String extraIncomeFrom,
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal monthlyExpenses,
            @NotNull @Size(max = 50) @Valid List<OneOffExpense> oneOffExpenses) {
    }

    /** Gasto puntual de la conjunta en un mes concreto: un seguro, un viaje. */
    public record OneOffExpense(
            @NotBlank @Size(max = 60) String name,
            @NotNull @PositiveOrZero @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @NotNull @Pattern(regexp = MONTH, message = "El mes del gasto tiene formato yyyy-MM")
            String month) {
    }
}
