package com.gastos.iam.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Fila de la tabla {@code household}. */
@Entity
@Table(name = "household")
public class HouseholdEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    protected HouseholdEntity() {
        // Requerido por JPA.
    }

    public HouseholdEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
