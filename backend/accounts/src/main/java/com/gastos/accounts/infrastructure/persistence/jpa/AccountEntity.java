package com.gastos.accounts.infrastructure.persistence.jpa;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fila de la tabla {@code account}.
 *
 * <p>Es una estructura de datos plana, no el modelo. El agregado {@code Account} vive en
 * el dominio sin anotaciones y sin saber que existe JPA; la traduccion entre ambos la
 * hace {@link AccountJpaMapper}.</p>
 *
 * <p>Separarlos cuesta una clase y un mapper, y a cambio evita que el esquema de la base
 * de datos dicte las invariantes de negocio: JPA necesita constructor vacio y setters,
 * que es justo lo que un agregado no debe ofrecer.</p>
 */
@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 60)
    private String alias;

    @Column(name = "bank_name", nullable = false, length = 60)
    private String bankName;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 20)
    private String ownership;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(name = "balance_updated_at", nullable = false)
    private Instant balanceUpdatedAt;

    // EAGER a proposito: son dos titulares como maximo y siempre se necesitan al
    // reconstruir el agregado, asi que diferirlos solo produciria consultas N+1.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_holder",
            joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "user_id", nullable = false)
    private Set<UUID> holders = new LinkedHashSet<>();

    protected AccountEntity() {
        // Requerido por JPA.
    }

    public AccountEntity(UUID id, UUID householdId, String alias, String bankName, String type,
                         String ownership, BigDecimal balance, Instant balanceUpdatedAt,
                         Set<UUID> holders) {
        this.id = id;
        this.householdId = householdId;
        this.alias = alias;
        this.bankName = bankName;
        this.type = type;
        this.ownership = ownership;
        this.balance = balance;
        this.balanceUpdatedAt = balanceUpdatedAt;
        this.holders = new LinkedHashSet<>(holders);
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdId() {
        return householdId;
    }

    public String getAlias() {
        return alias;
    }

    public String getBankName() {
        return bankName;
    }

    public String getType() {
        return type;
    }

    public String getOwnership() {
        return ownership;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getBalanceUpdatedAt() {
        return balanceUpdatedAt;
    }

    public Set<UUID> getHolders() {
        return holders;
    }
}
