package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Programa publico de ayuda a la compra de vivienda.
 *
 * <p>Es un <strong>agregado editable desde la aplicacion</strong>, no una clase con los
 * requisitos escritos en el codigo. La razon es practica: las convocatorias cambian,
 * conviven varias a la vez y sus condiciones exactas hay que contrastarlas con el
 * boletin oficial. Si estuvieran compiladas, cada cambio normativo seria un despliegue.</p>
 *
 * <p>Un programa solo decide <strong>cuanto presta el banco</strong> (el LTV maximo). No
 * toca el calculo de la cuota, ni el DTI, ni el veredicto de viabilidad: esos son
 * identicos se aplique el programa que se aplique o ninguno.</p>
 *
 * <p>Los limites nulos significan "sin limite": un programa sin edad maxima admite
 * cualquier edad.</p>
 *
 * <p>La edad maxima es <strong>inclusiva</strong>: 40 significa "no superar los 40", que
 * es literalmente como lo escribe el BOCM. Un programa con tramos por edad —Mi Primera
 * Vivienda los tiene desde agosto de 2026— se representa como varios programas; el modo
 * automatico elige el que mas financie de entre los que se cumplen, que es justo el
 * tramo que corresponde.</p>
 */
public final class AidProgram {

    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_SOURCE_NOTE_LENGTH = 300;

    private final UUID id;
    private final HouseholdId householdId;
    private String name;
    private Percentage maxLoanToValue;
    private Money maxPropertyPrice;
    private Integer maxApplicantAge;
    private boolean requiresFirstHome;
    /** Solo para familias con hijos menores a cargo, numerosas o monoparentales. */
    private boolean requiresFamily;
    private boolean active;
    private String sourceNote;

    private AidProgram(UUID id, HouseholdId householdId, String name, Percentage maxLoanToValue,
                       Money maxPropertyPrice, Integer maxApplicantAge, boolean requiresFirstHome,
                       boolean requiresFamily, boolean active, String sourceNote) {
        this.id = Guard.notNull(id, "id");
        this.householdId = Guard.notNull(householdId, "householdId");
        this.name = validateName(name);
        this.maxLoanToValue = validateLoanToValue(maxLoanToValue);
        this.maxPropertyPrice = validateMaxPrice(maxPropertyPrice);
        this.maxApplicantAge = validateMaxAge(maxApplicantAge);
        this.requiresFirstHome = requiresFirstHome;
        this.requiresFamily = requiresFamily;
        this.active = active;
        this.sourceNote = validateSourceNote(sourceNote);
    }

    public static AidProgram create(HouseholdId householdId, String name, Percentage maxLoanToValue,
                                    Money maxPropertyPrice, Integer maxApplicantAge,
                                    boolean requiresFirstHome, boolean requiresFamily,
                                    boolean active, String sourceNote) {
        return new AidProgram(UUID.randomUUID(), householdId, name, maxLoanToValue, maxPropertyPrice,
                maxApplicantAge, requiresFirstHome, requiresFamily, active, sourceNote);
    }

    /** Programa sin requisito familiar. */
    public static AidProgram create(HouseholdId householdId, String name, Percentage maxLoanToValue,
                                    Money maxPropertyPrice, Integer maxApplicantAge,
                                    boolean requiresFirstHome, boolean active, String sourceNote) {
        return create(householdId, name, maxLoanToValue, maxPropertyPrice, maxApplicantAge,
                requiresFirstHome, false, active, sourceNote);
    }

    public static AidProgram rehydrate(UUID id, HouseholdId householdId, String name,
                                       Percentage maxLoanToValue, Money maxPropertyPrice,
                                       Integer maxApplicantAge, boolean requiresFirstHome,
                                       boolean requiresFamily, boolean active, String sourceNote) {
        return new AidProgram(id, householdId, name, maxLoanToValue, maxPropertyPrice, maxApplicantAge,
                requiresFirstHome, requiresFamily, active, sourceNote);
    }

    public static AidProgram rehydrate(UUID id, HouseholdId householdId, String name,
                                       Percentage maxLoanToValue, Money maxPropertyPrice,
                                       Integer maxApplicantAge, boolean requiresFirstHome,
                                       boolean active, String sourceNote) {
        return rehydrate(id, householdId, name, maxLoanToValue, maxPropertyPrice, maxApplicantAge,
                requiresFirstHome, false, active, sourceNote);
    }

    public void update(String newName, Percentage newLoanToValue, Money newMaxPropertyPrice,
                       Integer newMaxApplicantAge, boolean newRequiresFirstHome, boolean newActive,
                       String newSourceNote) {
        update(newName, newLoanToValue, newMaxPropertyPrice, newMaxApplicantAge, newRequiresFirstHome,
                requiresFamily, newActive, newSourceNote);
    }

    public void update(String newName, Percentage newLoanToValue, Money newMaxPropertyPrice,
                       Integer newMaxApplicantAge, boolean newRequiresFirstHome,
                       boolean newRequiresFamily, boolean newActive, String newSourceNote) {
        this.name = validateName(newName);
        this.maxLoanToValue = validateLoanToValue(newLoanToValue);
        this.maxPropertyPrice = validateMaxPrice(newMaxPropertyPrice);
        this.maxApplicantAge = validateMaxAge(newMaxApplicantAge);
        this.requiresFirstHome = newRequiresFirstHome;
        this.requiresFamily = newRequiresFamily;
        this.active = newActive;
        this.sourceNote = validateSourceNote(newSourceNote);
    }

    /**
     * Comprueba la operacion contra los requisitos y devuelve el resultado con los
     * motivos concretos del incumplimiento.
     *
     * <p>Se devuelven todos los requisitos que fallan, no solo el primero: si no cumples
     * ni la edad ni el precio, conviene saberlo de una vez.</p>
     */
    public ProgramEligibility evaluate(Money propertyPrice, ApplicantProfile applicant) {
        Guard.notNull(propertyPrice, "propertyPrice");
        Guard.notNull(applicant, "applicant");
        List<String> unmet = new ArrayList<>();

        if (maxPropertyPrice != null && propertyPrice.isGreaterThan(maxPropertyPrice)) {
            unmet.add("El precio de " + propertyPrice + " supera el maximo de " + maxPropertyPrice);
        }
        if (maxApplicantAge != null && applicant.age() > maxApplicantAge) {
            unmet.add("La edad de " + applicant.age() + " supera el maximo de " + maxApplicantAge);
        }
        if (requiresFirstHome && !applicant.firstHome()) {
            unmet.add("El programa exige que sea la primera vivienda en propiedad");
        }
        // Los programas de primera vivienda exigen ademas vivir en ella: no hay ninguno
        // que financie una compra para alquilar.
        if (requiresFirstHome && !applicant.primaryResidence()) {
            unmet.add("El programa exige destinarla a vivienda habitual");
        }
        if (requiresFamily && !(applicant.familyWithChildren() || applicant.largeFamily())) {
            unmet.add("El programa es para familias con hijos menores a cargo, numerosas o "
                    + "monoparentales");
        }

        return new ProgramEligibility(id, name, maxLoanToValue, active, unmet.isEmpty() && active,
                List.copyOf(unmet));
    }

    public boolean isAccessibleBy(HouseholdId requesterHousehold) {
        return householdId.equals(requesterHousehold);
    }

    private static String validateName(String value) {
        String trimmed = Guard.notBlank(value, "name").trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new DomainException("El nombre del programa supera los " + MAX_NAME_LENGTH
                    + " caracteres");
        }
        return trimmed;
    }

    private static Percentage validateLoanToValue(Percentage value) {
        Guard.notNull(value, "maxLoanToValue");
        if (value.value().signum() <= 0 || value.isGreaterThan(Percentage.of("100.00"))) {
            throw new DomainException("El LTV maximo del programa debe estar entre 0 y 100, recibido: "
                    + value);
        }
        return value;
    }

    private static Money validateMaxPrice(Money value) {
        if (value != null && !value.isPositive()) {
            throw new DomainException("El precio maximo del programa debe ser mayor que cero");
        }
        return value;
    }

    private static Integer validateMaxAge(Integer value) {
        if (value != null) {
            Guard.inRange(value, 18, 120, "maxApplicantAge");
        }
        return value;
    }

    private static String validateSourceNote(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_SOURCE_NOTE_LENGTH) {
            throw new DomainException("La nota de origen supera los " + MAX_SOURCE_NOTE_LENGTH
                    + " caracteres");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public HouseholdId householdId() {
        return householdId;
    }

    public String name() {
        return name;
    }

    public Percentage maxLoanToValue() {
        return maxLoanToValue;
    }

    /** {@code null} significa sin limite de precio. */
    public Money maxPropertyPrice() {
        return maxPropertyPrice;
    }

    /** {@code null} significa sin limite de edad. */
    public Integer maxApplicantAge() {
        return maxApplicantAge;
    }

    public boolean requiresFirstHome() {
        return requiresFirstHome;
    }

    public boolean requiresFamily() {
        return requiresFamily;
    }

    public boolean isActive() {
        return active;
    }

    /** De donde salen estas cifras. Sirve para saber que hay que reverificar y cuando. */
    public String sourceNote() {
        return sourceNote;
    }
}
