package com.gastos.mortgage.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fila de la tabla {@code aid_program}.
 *
 * <p>{@code maxPropertyPrice} y {@code maxApplicantAge} admiten nulo porque "sin limite"
 * es un estado real del negocio, distinto de cero.</p>
 */
@Entity
@Table(name = "aid_program")
public class AidProgramEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "max_loan_to_value", nullable = false, precision = 7, scale = 4)
    private BigDecimal maxLoanToValue;

    @Column(name = "max_property_price", precision = 15, scale = 2)
    private BigDecimal maxPropertyPrice;

    @Column(name = "max_applicant_age")
    private Integer maxApplicantAge;

    @Column(name = "requires_first_home", nullable = false)
    private boolean requiresFirstHome;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "source_note", nullable = false, length = 300)
    private String sourceNote;

    protected AidProgramEntity() {
        // Requerido por JPA.
    }

    public AidProgramEntity(UUID id, UUID householdId, String name, BigDecimal maxLoanToValue,
                            BigDecimal maxPropertyPrice, Integer maxApplicantAge,
                            boolean requiresFirstHome, boolean active, String sourceNote) {
        this.id = id;
        this.householdId = householdId;
        this.name = name;
        this.maxLoanToValue = maxLoanToValue;
        this.maxPropertyPrice = maxPropertyPrice;
        this.maxApplicantAge = maxApplicantAge;
        this.requiresFirstHome = requiresFirstHome;
        this.active = active;
        this.sourceNote = sourceNote;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMaxLoanToValue() {
        return maxLoanToValue;
    }

    public BigDecimal getMaxPropertyPrice() {
        return maxPropertyPrice;
    }

    public Integer getMaxApplicantAge() {
        return maxApplicantAge;
    }

    public boolean isRequiresFirstHome() {
        return requiresFirstHome;
    }

    public boolean isActive() {
        return active;
    }

    public String getSourceNote() {
        return sourceNote;
    }
}
