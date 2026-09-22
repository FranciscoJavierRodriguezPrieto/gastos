package com.gastos.expenses.application;

import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.model.FixedExpenseId;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.expenses.domain.port.FixedExpenseRepository;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.ResourceNotFoundException;
import com.gastos.shared.domain.UserId;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gastos fijos: el alquiler, el telefono, la cuota del gimnasio.
 *
 * <p>Dos responsabilidades: mantener las plantillas, y <strong>expandirlas</strong> al
 * gasto real de cada mes.</p>
 *
 * <h2>Por que la expansion es perezosa</h2>
 *
 * <p>Lo natural seria una tarea programada que el dia 1 generase los gastos del mes. No
 * sirve aqui: la API se despliega en una instancia gratuita que <strong>duerme cuando
 * nadie la usa</strong>, asi que el dia 1 a las 00:00 lo mas probable es que no haya
 * nadie escuchando. Una tarea programada sobre un servicio apagado no es una tarea
 * programada, es una que a veces se ejecuta.</p>
 *
 * <p>En vez de eso se expande <strong>al abrir el mes</strong>: quien mira marzo provoca
 * que marzo se genere. Es idempotente —la marca de aplicado impide repetirlo— y no
 * depende de que nadie este despierto a una hora concreta. Como efecto secundario
 * agradable, los meses que nadie abre no generan filas.</p>
 *
 * <p>El precio es que una lectura escribe. Se asume a conciencia: la alternativa era un
 * mecanismo que falla justo cuando el servicio esta dormido, que es casi siempre.</p>
 */
public class ManageFixedExpensesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ManageFixedExpensesUseCase.class);

    private final FixedExpenseRepository fixedExpenses;
    private final ExpenseRepository expenses;
    private final Clock clock;

    public ManageFixedExpensesUseCase(FixedExpenseRepository fixedExpenses,
                                      ExpenseRepository expenses, Clock clock) {
        this.fixedExpenses = Guard.notNull(fixedExpenses, "fixedExpenses");
        this.expenses = Guard.notNull(expenses, "expenses");
        this.clock = Guard.notNull(clock, "clock");
    }

    public List<FixedExpense> list(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return fixedExpenses.findAll(householdId);
    }

    public FixedExpense findById(HouseholdId householdId, FixedExpenseId fixedExpenseId) {
        return requireOwned(householdId, fixedExpenseId);
    }

    /**
     * Da de alta un gasto fijo.
     *
     * <p>Empieza a contar en el mes que se indique o, por omision, en el mes en curso.
     * No se admite empezar en el pasado: generaria gastos en meses ya cerrados y
     * cambiaria totales que alguien ya ha dado por buenos.</p>
     */
    public FixedExpense create(HouseholdId householdId, UserId createdBy,
                               FixedExpenseCommand command) {
        Guard.notNull(command, "command");
        YearMonth inicio = command.startMonth() == null ? currentMonth() : command.startMonth();
        if (inicio.isBefore(currentMonth())) {
            throw new com.gastos.shared.domain.DomainException(
                    "Un gasto fijo no puede empezar en un mes ya pasado");
        }

        FixedExpense fijo = FixedExpense.create(householdId, createdBy, command.description(),
                command.amount(), command.category(), command.dayOfMonth(), command.accountId(),
                inicio);

        log.info("Gasto fijo dado de alta en el hogar {} desde {}", householdId, inicio);
        return fixedExpenses.save(fijo);
    }

    /**
     * Cambia la plantilla.
     *
     * <p><strong>Solo afecta a los meses que aun no se han generado.</strong> Si el
     * alquiler sube, los meses ya pasados conservan lo que de verdad se pago, y el mes en
     * curso tambien si ya estaba generado: para ese se edita el gasto directamente.</p>
     */
    public FixedExpense update(HouseholdId householdId, FixedExpenseId fixedExpenseId,
                               FixedExpenseCommand command) {
        Guard.notNull(command, "command");
        FixedExpense fijo = requireOwned(householdId, fixedExpenseId);
        fijo.update(command.description(), command.amount(), command.category(),
                command.dayOfMonth());
        return fixedExpenses.save(fijo);
    }

    /**
     * Da de baja el gasto fijo a partir del mes que viene.
     *
     * <p>Del mes que viene y no de este: lo de este mes normalmente ya se ha pagado. Si
     * ademas no se quiere contar este mes, se borra el gasto generado, que es una accion
     * distinta y explicita.</p>
     */
    public FixedExpense discontinue(HouseholdId householdId, FixedExpenseId fixedExpenseId) {
        FixedExpense fijo = requireOwned(householdId, fixedExpenseId);
        fijo.discontinueFrom(currentMonth().plusMonths(1));
        log.info("Gasto fijo {} dado de baja desde {}", fixedExpenseId, fijo.endMonth());
        return fixedExpenses.save(fijo);
    }

    public FixedExpense reactivate(HouseholdId householdId, FixedExpenseId fixedExpenseId) {
        FixedExpense fijo = requireOwned(householdId, fixedExpenseId);
        fijo.reactivate();
        return fixedExpenses.save(fijo);
    }

    /**
     * Borra la plantilla del todo.
     *
     * <p>Los gastos que ya genero <strong>se quedan</strong>: son dinero que se gasto de
     * verdad. Dejar de generar y borrar el historico son cosas distintas, y confundirlas
     * haria que quitar el gimnasio en junio cambiase los totales de marzo.</p>
     */
    public void delete(HouseholdId householdId, FixedExpenseId fixedExpenseId) {
        requireOwned(householdId, fixedExpenseId);
        fixedExpenses.delete(householdId, fixedExpenseId);
        log.info("Gasto fijo {} borrado; sus gastos ya generados se conservan", fixedExpenseId);
    }

    /**
     * Genera los gastos que falten de ese mes. Idempotente: llamarla mil veces deja el
     * mismo resultado que llamarla una.
     *
     * @return cuantos gastos se han generado, para poder verlo en los registros
     */
    public int expand(HouseholdId householdId, YearMonth month) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(month, "month");

        List<FixedExpense> plantillas = fixedExpenses.findAll(householdId);
        if (plantillas.isEmpty()) {
            return 0;
        }

        Set<FixedExpenseId> yaAplicados = fixedExpenses.findAppliedIn(householdId, month);
        int generados = 0;

        for (FixedExpense fijo : plantillas) {
            if (!fijo.appliesTo(month) || yaAplicados.contains(fijo.id())) {
                continue;
            }
            // Primero se reserva el mes y solo despues se genera el gasto. El orden
            // inverso es el que parece natural —crear y luego apuntar que se ha creado—
            // y es justo el que duplica: dos peticiones simultaneas crearian dos gastos
            // antes de que ninguna llegase a apuntar nada. Y ocurre a diario, porque la
            // pantalla pide el listado y el resumen a la vez.
            //
            // Reservar primero convierte la clave primaria de la tabla de marcas en el
            // arbitro: solo una peticion se lleva el mes.
            if (!fixedExpenses.claimFor(fijo.id(), month)) {
                continue;
            }
            expenses.save(fijo.materialiseFor(month));
            generados++;
        }

        if (generados > 0) {
            log.info("Generados {} gastos fijos del hogar {} para {}", generados, householdId, month);
        }
        return generados;
    }

    private YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    /**
     * Recupera la plantilla comprobando que es del hogar que la pide.
     *
     * <p>Mismo mensaje exista o no: distinguirlos permitiria enumerar identificadores
     * ajenos (OWASP API1), igual que en el resto del contexto de gastos.</p>
     */
    private FixedExpense requireOwned(HouseholdId householdId, FixedExpenseId fixedExpenseId) {
        Guard.notNull(householdId, "householdId");
        Guard.notNull(fixedExpenseId, "fixedExpenseId");
        FixedExpense fijo = fixedExpenses.findById(householdId, fixedExpenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto fijo no encontrado"));
        if (!fijo.isAccessibleBy(householdId)) {
            throw new ResourceNotFoundException("Gasto fijo no encontrado");
        }
        return fijo;
    }
}
