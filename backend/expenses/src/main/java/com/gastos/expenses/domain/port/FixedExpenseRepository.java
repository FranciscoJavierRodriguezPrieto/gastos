package com.gastos.expenses.domain.port;

import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.model.FixedExpenseId;
import com.gastos.shared.domain.HouseholdId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Puerto de salida de los gastos fijos. Siempre acotado por hogar. */
public interface FixedExpenseRepository {

    Optional<FixedExpense> findById(HouseholdId householdId, FixedExpenseId fixedExpenseId);

    /**
     * Todos los gastos fijos del hogar, vigentes o no.
     *
     * <p>Se devuelven enteros y la vigencia la decide el dominio
     * ({@code FixedExpense.appliesTo}) en lugar de filtrarse aqui con SQL. Un hogar tiene
     * una decena de gastos fijos, asi que no hay nada que optimizar, y a cambio la regla
     * de cuando toca generar vive en un unico sitio.</p>
     */
    List<FixedExpense> findAll(HouseholdId householdId);

    /**
     * Los gastos fijos que ya se expandieron en ese mes.
     *
     * <p>Es lo que hace que la expansion sea idempotente y que borrar sea definitivo: si
     * alguien borra el gasto generado de un mes, la marca sigue ahi y no vuelve a
     * aparecer. Sin esto, borrar seria un deseo que se deshace al recargar.</p>
     */
    Set<FixedExpenseId> findAppliedIn(HouseholdId householdId, YearMonth month);

    /**
     * Reserva ese mes para esa plantilla, y dice si la reserva es nuestra.
     *
     * <p>Devuelve {@code false} si <strong>otro ya la tenia</strong>. No es un fallo: es
     * el caso normal cuando dos peticiones expanden el mismo mes a la vez, que ocurre
     * sin ir mas lejos cada vez que la pantalla pide el listado y el resumen en paralelo.
     * Quien pierde la carrera simplemente no genera nada.</p>
     *
     * <p>El arbitro es la clave primaria de la tabla de marcas, no una comprobacion
     * previa en memoria: entre un {@code exists} y el {@code insert} cabe perfectamente
     * la otra peticion.</p>
     */
    boolean claimFor(FixedExpenseId fixedExpenseId, YearMonth month);

    FixedExpense save(FixedExpense fixedExpense);

    /** Borra la plantilla. Los gastos que ya genero son historia y se quedan. */
    void delete(HouseholdId householdId, FixedExpenseId fixedExpenseId);
}
