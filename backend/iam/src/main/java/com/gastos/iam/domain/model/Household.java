package com.gastos.iam.domain.model;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raiz de agregado del hogar. Impone el limite funcional del producto: como maximo
 * dos convivientes. Los ingresos del hogar (base del DTI de la hipoteca) se calculan
 * aqui y no en la capa de presentacion.
 */
public final class Household {

    public static final int MAX_MEMBERS = 2;

    private final HouseholdId id;
    private String name;
    private final List<User> members;

    private Household(HouseholdId id, String name, List<User> members) {
        this.id = Guard.notNull(id, "id");
        this.name = Guard.notBlank(name, "name");
        this.members = new ArrayList<>(Guard.notNull(members, "members"));
    }

    public static Household create(String name, User owner) {
        Guard.notNull(owner, "owner");
        if (owner.role() != Role.OWNER) {
            throw new DomainException("El creador del hogar debe tener rol OWNER");
        }
        return new Household(owner.householdId(), name, List.of(owner));
    }

    public static Household rehydrate(HouseholdId id, String name, List<User> members) {
        return new Household(id, name, members);
    }

    public void addMember(User member) {
        Guard.notNull(member, "member");
        if (!member.belongsTo(id)) {
            throw new DomainException("El usuario no pertenece a este hogar");
        }
        if (members.size() >= MAX_MEMBERS) {
            throw new DomainException("El hogar admite como maximo " + MAX_MEMBERS + " miembros");
        }
        boolean alreadyPresent = members.stream()
                .anyMatch(existing -> existing.email().equals(member.email()));
        if (alreadyPresent) {
            throw new DomainException("Ya existe un miembro con ese correo en el hogar");
        }
        members.add(member);
    }

    /** Ingresos netos mensuales agregados: numerador de referencia del analisis de viabilidad. */
    public Money monthlyNetIncome() {
        return members.stream()
                .map(User::monthlyNetIncome)
                .reduce(Money.zero(), Money::plus);
    }

    public boolean contains(UserId userId) {
        return members.stream().anyMatch(member -> member.id().equals(userId));
    }

    public void rename(String newName) {
        this.name = Guard.notBlank(newName, "name");
    }

    public HouseholdId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<User> members() {
        return Collections.unmodifiableList(members);
    }
}
