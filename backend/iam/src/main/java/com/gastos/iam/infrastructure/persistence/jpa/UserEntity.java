package com.gastos.iam.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Fila de la tabla {@code app_user}. */
@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "monthly_net_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyNetIncome;

    protected UserEntity() {
        // Requerido por JPA.
    }

    public UserEntity(UUID id, UUID householdId, String email, String displayName, String role,
                      BigDecimal monthlyNetIncome) {
        this.id = id;
        this.householdId = householdId;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.monthlyNetIncome = monthlyNetIncome;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }

    public BigDecimal getMonthlyNetIncome() {
        return monthlyNetIncome;
    }
}
