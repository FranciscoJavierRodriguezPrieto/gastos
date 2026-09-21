package com.gastos.expenses.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Gasto fijo: el alquiler, el telefono, la cuota del gimnasio. Lo que esta todos los
 * meses y no apetece volver a teclear.
 *
 * <p>Esto es una <strong>plantilla</strong>, no un gasto. Cada mes que se abre, la
 * plantilla se convierte en un {@link Expense} de verdad
 * ({@link #materialiseFor(YearMonth)}), y a partir de ahi ese gasto es uno normal y
 * corriente: se edita, se borra y cuenta en los totales como cualquier otro.</p>
 *
 * <p>De esa separacion salen tres comportamientos que son justo los que se buscaban:</p>
 *
 * <ol>
 *   <li><strong>Editar un mes no toca los demas.</strong> La luz son 50 EUR, pero en
 *       enero fueron 95: se corrige el gasto de enero y febrero sigue saliendo a 50,
 *       porque lo que se edito fue el gasto generado y no la plantilla.</li>
 *   <li><strong>Editar la plantilla no reescribe el pasado.</strong> Si el alquiler sube
 *       a 850, los meses ya generados conservan los 800 que de verdad se pagaron. El
 *       historico sale gratis: no hace falta versionar importes porque cada mes ya
 *       guarda el suyo.</li>
 *   <li><strong>Darla de baja no borra la historia.</strong> Dejar de generar y borrar lo
 *       ya gastado son cosas distintas.</li>
 * </ol>
 *
 * <p>La contrapartida honesta: cambiar la plantilla <em>no</em> cambia el mes en curso si
 * ya estaba generado. Para eso se edita el gasto de ese mes. Es mas tecleo en ese caso
 * concreto, a cambio de que nada reescriba cifras pasadas por su cuenta.</p>
 */
public final class FixedExpense {

    private static final int MAX_DESCRIPTION_LENGTH = 140;

    /**
     * El dia de cargo se limita a 28 y no a 31.
     *
     * <p>Un gasto fijo el dia 31 no existe en febrero, ni en abril, ni en cuatro meses
     * mas. Recortar la eleccion evita tener que decidir en el momento de generar si ese
     * cargo cae el ultimo dia del mes o el primero del siguiente, que es una decision
     * que nadie quiere descubrir en un total que no cuadra.</p>
     */
    public static final int MAX_DAY_OF_MONTH = 28;

    private final FixedExpenseId id;
    private final HouseholdId householdId;
    private final UserId createdBy;
    private String description;
    private Money amount;
    private ExpenseCategory category;
    private int dayOfMonth;
    private final UUID accountId;
    private final YearMonth startMonth;
    private YearMonth endMonth;

    private FixedExpense(FixedExpenseId id, HouseholdId householdId, UserId createdBy,
                         String description, Money amount, ExpenseCategory category,
                         int dayOfMonth, UUID accountId, YearMonth startMonth,
                         YearMonth endMonth) {
        this.id = Guard.notNull(id, "id");
        this.householdId = Guard.notNull(householdId, "householdId");
        this.createdBy = Guard.notNull(createdBy, "createdBy");
        this.description = validateDescription(description);
        this.amount = validateAmount(amount);
        this.category = Guard.notNull(category, "category");
        this.dayOfMonth = Guard.inRange(dayOfMonth, 1, MAX_DAY_OF_MONTH, "dayOfMonth");
        this.accountId = accountId;
        this.startMonth = Guard.notNull(startMonth, "startMonth");
        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new DomainException("La baja no puede ser anterior al alta");
        }
        this.endMonth = endMonth;
    }

    public static FixedExpense create(HouseholdId householdId, UserId createdBy, String description,
                                      Money amount, ExpenseCategory category, int dayOfMonth,
                                      UUID accountId, YearMonth startMonth) {
        return new FixedExpense(FixedExpenseId.newId(), householdId, createdBy, description, amount,
                category, dayOfMonth, accountId, startMonth, null);
    }

    public static FixedExpense rehydrate(FixedExpenseId id, HouseholdId householdId, UserId createdBy,
                                         String description, Money amount, ExpenseCategory category,
                                         int dayOfMonth, UUID accountId, YearMonth startMonth,
                                         YearMonth endMonth) {
        return new FixedExpense(id, householdId, createdBy, description, amount, category, dayOfMonth,
                accountId, startMonth, endMonth);
    }

    /**
     * Cambia los datos de la plantilla.
     *
     * <p>Solo afecta a los meses que todavia no se han generado. Lo ya generado es
     * historia y no se reescribe.</p>
     */
    public void update(String newDescription, Money newAmount, ExpenseCategory newCategory,
                       int newDayOfMonth) {
        this.description = validateDescription(newDescription);
        this.amount = validateAmount(newAmount);
        this.category = Guard.notNull(newCategory, "category");
        this.dayOfMonth = Guard.inRange(newDayOfMonth, 1, MAX_DAY_OF_MONTH, "dayOfMonth");
    }

    /** Deja de generar a partir de ese mes, incluido. Lo ya generado se queda. */
    public void discontinueFrom(YearMonth month) {
        Guard.notNull(month, "month");
        if (month.isBefore(startMonth)) {
            throw new DomainException("La baja no puede ser anterior al alta");
        }
        this.endMonth = month;
    }

    /** Vuelve a estar vigente, sin fecha de baja. */
    public void reactivate() {
        this.endMonth = null;
    }

    /** Si toca generar gasto en ese mes. */
    public boolean appliesTo(YearMonth month) {
        Guard.notNull(month, "month");
        if (month.isBefore(startMonth)) {
            return false;
        }
        return endMonth == null || month.isBefore(endMonth);
    }

    public boolean isActive() {
        return endMonth == null;
    }

    /**
     * Convierte la plantilla en el gasto real de ese mes.
     *
     * <p>Se genera con periodicidad {@link Recurrence#MENSUAL} porque eso es lo que es, y
     * asi el analisis de la hipoteca lo computa como compromiso estable sin ningun caso
     * especial.</p>
     */
    public Expense materialiseFor(YearMonth month) {
        if (!appliesTo(month)) {
            throw new DomainException("Este gasto fijo no esta vigente en " + month);
        }
        LocalDate fecha = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        return Expense.fromFixedExpense(householdId, createdBy, description, amount, category,
                fecha, accountId, id);
    }

    public boolean isAccessibleBy(HouseholdId requesterHousehold) {
        return householdId.equals(requesterHousehold);
    }

    /** Lo que suma al mes. Util para enseñar el total de los fijos de un vistazo. */
    public Money monthlyCost() {
        return amount;
    }

    private static String validateDescription(String value) {
        String trimmed = Guard.notBlank(value, "description").trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new DomainException(
                    "La descripcion supera los " + MAX_DESCRIPTION_LENGTH + " caracteres");
        }
        return trimmed;
    }

    private static Money validateAmount(Money value) {
        Guard.notNull(value, "amount");
        if (!value.isPositive()) {
            throw new DomainException("El importe del gasto fijo debe registrarse en positivo");
        }
        return value;
    }

    public FixedExpenseId id() {
        return id;
    }

    public HouseholdId householdId() {
        return householdId;
    }

    public UserId createdBy() {
        return createdBy;
    }

    public String description() {
        return description;
    }

    public Money amount() {
        return amount;
    }

    public ExpenseCategory category() {
        return category;
    }

    public int dayOfMonth() {
        return dayOfMonth;
    }

    public UUID accountId() {
        return accountId;
    }

    public YearMonth startMonth() {
        return startMonth;
    }

    public YearMonth endMonth() {
        return endMonth;
    }
}
