package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import java.time.Instant;
import java.util.UUID;

/**
 * Escenario de simulacion guardado por el hogar, para poder comparar alternativas
 * ("piso de 240k a 25 anos" frente a "piso de 280k a 30 anos") a lo largo del tiempo.
 *
 * <p>Guarda la entrada, nunca el resultado: los tipos y las politicas cambian, asi que
 * el resultado se recalcula siempre con el motor vigente. Persistir el resultado seria
 * cachear una mentira.</p>
 */
public final class MortgageScenario {

    private static final int MAX_NAME_LENGTH = 60;

    private final UUID id;
    private final HouseholdId householdId;
    private String name;
    private SimulationRequest request;
    private final Instant createdAt;
    private Instant updatedAt;

    private MortgageScenario(UUID id, HouseholdId householdId, String name, SimulationRequest request,
                             Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "id");
        this.householdId = Guard.notNull(householdId, "householdId");
        this.name = validateName(name);
        this.request = Guard.notNull(request, "request");
        this.createdAt = Guard.notNull(createdAt, "createdAt");
        this.updatedAt = Guard.notNull(updatedAt, "updatedAt");
    }

    public static MortgageScenario create(HouseholdId householdId, String name,
                                          SimulationRequest request, Instant now) {
        return new MortgageScenario(UUID.randomUUID(), householdId, name, request, now, now);
    }

    public static MortgageScenario rehydrate(UUID id, HouseholdId householdId, String name,
                                             SimulationRequest request, Instant createdAt,
                                             Instant updatedAt) {
        return new MortgageScenario(id, householdId, name, request, createdAt, updatedAt);
    }

    public void updateRequest(SimulationRequest newRequest, Instant now) {
        this.request = Guard.notNull(newRequest, "request");
        this.updatedAt = Guard.notNull(now, "now");
    }

    public void rename(String newName, Instant now) {
        this.name = validateName(newName);
        this.updatedAt = Guard.notNull(now, "now");
    }

    public boolean isAccessibleBy(HouseholdId requesterHousehold) {
        return householdId.equals(requesterHousehold);
    }

    private static String validateName(String value) {
        String trimmed = Guard.notBlank(value, "name").trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new DomainException("El nombre del escenario supera los " + MAX_NAME_LENGTH
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

    public SimulationRequest request() {
        return request;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
