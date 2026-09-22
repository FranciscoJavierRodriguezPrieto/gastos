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
 * Raiz de agregado "Gasto". El importe se almacena siempre en positivo y el signo
 * lo aporta la semantica del concepto: evita el clasico error de sumar gastos
 * capturados unas veces en negativo y otras en positivo.
 */
public final class Expense {

    private static final int MAX_DESCRIPTION_LENGTH = 140;

    private final ExpenseId id;
    private final HouseholdId householdId;
    private final UserId registeredBy;
    private String description;
    private Money amount;
    private ExpenseCategory category;
    private Recurrence recurrence;
    private LocalDate incurredOn;
    private final UUID accountId;
    private final FixedExpenseId fixedExpenseId;

    private Expense(ExpenseId id, HouseholdId householdId, UserId registeredBy, String description,
                    Money amount, ExpenseCategory category, Recurrence recurrence, LocalDate incurredOn,
                    UUID accountId, FixedExpenseId fixedExpenseId) {
        this.id = Guard.notNull(id, "id");
        this.householdId = Guard.notNull(householdId, "householdId");
        this.registeredBy = Guard.notNull(registeredBy, "registeredBy");
        this.description = validateDescription(description);
        this.amount = validateAmount(amount);
        this.category = Guard.notNull(category, "category");
        this.recurrence = Guard.notNull(recurrence, "recurrence");
        this.incurredOn = Guard.notNull(incurredOn, "incurredOn");
        this.accountId = accountId;
        this.fixedExpenseId = fixedExpenseId;
    }

    public static Expense register(HouseholdId householdId, UserId registeredBy, String description,
                                   Money amount, ExpenseCategory category, Recurrence recurrence,
                                   LocalDate incurredOn, UUID accountId) {
        return new Expense(ExpenseId.newId(), householdId, registeredBy, description, amount, category,
                recurrence, incurredOn, accountId, null);
    }

    /**
     * Gasto generado a partir de un {@link FixedExpense}.
     *
     * <p>Nace atado a su plantilla y por lo demas es un gasto normal: se edita, se borra
     * y cuenta en los totales como cualquier otro. El vinculo solo sirve para no generarlo
     * dos veces y para que la pantalla pueda decir de donde sale.</p>
     */
    public static Expense fromFixedExpense(HouseholdId householdId, UserId registeredBy,
                                           String description, Money amount,
                                           ExpenseCategory category, LocalDate incurredOn,
                                           UUID accountId, FixedExpenseId fixedExpenseId) {
        return new Expense(ExpenseId.newId(), householdId, registeredBy, description, amount,
                category, Recurrence.MENSUAL, incurredOn, accountId,
                Guard.notNull(fixedExpenseId, "fixedExpenseId"));
    }

    public static Expense rehydrate(ExpenseId id, HouseholdId householdId, UserId registeredBy,
                                    String description, Money amount, ExpenseCategory category,
                                    Recurrence recurrence, LocalDate incurredOn, UUID accountId,
                                    FixedExpenseId fixedExpenseId) {
        return new Expense(id, householdId, registeredBy, description, amount, category, recurrence,
                incurredOn, accountId, fixedExpenseId);
    }

    public void recategorize(ExpenseCategory newCategory) {
        this.category = Guard.notNull(newCategory, "category");
    }

    public void correct(String newDescription, Money newAmount, LocalDate newDate) {
        this.description = validateDescription(newDescription);
        this.amount = validateAmount(newAmount);
        this.incurredOn = Guard.notNull(newDate, "incurredOn");
    }

    public void changeRecurrence(Recurrence newRecurrence) {
        this.recurrence = Guard.notNull(newRecurrence, "recurrence");
    }

    /** Coste mensual equivalente, base del KPI de superavit y del DTI. */
    public Money monthlyEquivalent() {
        return recurrence.toMonthlyEquivalent(amount);
    }

    /** Compromiso estable que la banca computa como deuda al estudiar la hipoteca. */
    public boolean isStableCommitment() {
        return recurrence.isRecurring() && category.isRecurringCommitment();
    }

    public boolean belongsTo(YearMonth month) {
        return YearMonth.from(incurredOn).equals(Guard.notNull(month, "month"));
    }

    public boolean isAccessibleBy(HouseholdId requesterHousehold) {
        return householdId.equals(requesterHousehold);
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
            throw new DomainException("El importe del gasto debe registrarse en positivo");
        }
        return value;
    }

    public ExpenseId id() {
        return id;
    }

    public HouseholdId householdId() {
        return householdId;
    }

    public UserId registeredBy() {
        return registeredBy;
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

    public Recurrence recurrence() {
        return recurrence;
    }

    public LocalDate incurredOn() {
        return incurredOn;
    }

    public UUID accountId() {
        return accountId;
    }

    /** La plantilla de la que salio, o null si se registro a mano. */
    public FixedExpenseId fixedExpenseId() {
        return fixedExpenseId;
    }

    public boolean comesFromFixedExpense() {
        return fixedExpenseId != null;
    }
}
