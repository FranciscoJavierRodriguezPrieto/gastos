package com.gastos.mortgage.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de la tabla {@code mortgage_scenario}.
 *
 * <p>Guarda la <strong>entrada</strong> de la simulacion en columnas explicitas, no el
 * resultado ni un JSON opaco. En columnas porque la estructura es fija y asi se puede
 * consultar y migrar; y solo la entrada porque los tipos y las politicas cambian, de
 * modo que una cuota almacenada hace meses seria un dato falso.</p>
 */
@Entity
@Table(name = "mortgage_scenario")
public class MortgageScenarioEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "property_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal propertyPrice;

    @Column(name = "available_savings", nullable = false, precision = 15, scale = 2)
    private BigDecimal availableSavings;

    @Column(name = "target_reserve", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetReserve;

    @Column(name = "annual_nominal_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal annualNominalRate;

    @Column(name = "term_years", nullable = false)
    private int termYears;

    @Column(name = "net_monthly_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal netMonthlyIncome;

    @Column(name = "other_monthly_debts", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherMonthlyDebts;

    @Column(name = "applicant_age", nullable = false)
    private int applicantAge;

    @Column(name = "first_home", nullable = false)
    private boolean firstHome;

    @Column(name = "financing_mode", nullable = false, length = 20)
    private String financingMode;

    @Column(name = "financing_program_id")
    private UUID financingProgramId;

    @Column(name = "manual_loan_to_value", precision = 7, scale = 4)
    private BigDecimal manualLoanToValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MortgageScenarioEntity() {
        // Requerido por JPA.
    }

    public MortgageScenarioEntity(UUID id, UUID householdId, String name, BigDecimal propertyPrice,
                                  BigDecimal availableSavings, BigDecimal targetReserve,
                                  BigDecimal annualNominalRate, int termYears,
                                  BigDecimal netMonthlyIncome, BigDecimal otherMonthlyDebts,
                                  int applicantAge, boolean firstHome, String financingMode,
                                  UUID financingProgramId, BigDecimal manualLoanToValue,
                                  Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.householdId = householdId;
        this.name = name;
        this.propertyPrice = propertyPrice;
        this.availableSavings = availableSavings;
        this.targetReserve = targetReserve;
        this.annualNominalRate = annualNominalRate;
        this.termYears = termYears;
        this.netMonthlyIncome = netMonthlyIncome;
        this.otherMonthlyDebts = otherMonthlyDebts;
        this.applicantAge = applicantAge;
        this.firstHome = firstHome;
        this.financingMode = financingMode;
        this.financingProgramId = financingProgramId;
        this.manualLoanToValue = manualLoanToValue;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public BigDecimal getPropertyPrice() {
        return propertyPrice;
    }

    public BigDecimal getAvailableSavings() {
        return availableSavings;
    }

    public BigDecimal getTargetReserve() {
        return targetReserve;
    }

    public BigDecimal getAnnualNominalRate() {
        return annualNominalRate;
    }

    public int getTermYears() {
        return termYears;
    }

    public BigDecimal getNetMonthlyIncome() {
        return netMonthlyIncome;
    }

    public BigDecimal getOtherMonthlyDebts() {
        return otherMonthlyDebts;
    }

    public int getApplicantAge() {
        return applicantAge;
    }

    public boolean isFirstHome() {
        return firstHome;
    }

    public String getFinancingMode() {
        return financingMode;
    }

    public UUID getFinancingProgramId() {
        return financingProgramId;
    }

    public BigDecimal getManualLoanToValue() {
        return manualLoanToValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
