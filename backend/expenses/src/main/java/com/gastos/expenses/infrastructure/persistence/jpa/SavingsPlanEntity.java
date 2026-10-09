package com.gastos.expenses.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Fila de {@code savings_plan}. */
@Entity
@Table(name = "savings_plan")
public class SavingsPlanEntity {

    @Id
    @Column(name = "household_id")
    private UUID householdId;

    @Column(nullable = false, length = 20000)
    private String document;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SavingsPlanEntity() {
        // Requerido por JPA.
    }

    public SavingsPlanEntity(UUID householdId, String document) {
        this.householdId = householdId;
        this.document = document;
        this.updatedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

    public String getDocument() {
        return document;
    }
}
