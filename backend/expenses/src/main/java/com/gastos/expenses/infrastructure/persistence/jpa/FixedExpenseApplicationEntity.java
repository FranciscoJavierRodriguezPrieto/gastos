package com.gastos.expenses.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila de {@code fixed_expense_application}: esta plantilla ya genero su gasto en este mes.
 *
 * <p>La clave es la pareja entera, sin identificador propio, porque el hecho que
 * representa es exactamente esa pareja. Ademas asi la propia base de datos impide
 * generarlo dos veces aunque dos peticiones entren a la vez.</p>
 */
@Entity
@Table(name = "fixed_expense_application")
public class FixedExpenseApplicationEntity {

    @EmbeddedId
    private Key id;

    protected FixedExpenseApplicationEntity() {
        // Requerido por JPA.
    }

    public FixedExpenseApplicationEntity(UUID fixedExpenseId, LocalDate month) {
        this.id = new Key(fixedExpenseId, month);
    }

    public Key getId() {
        return id;
    }

    /**
     * Clave compuesta: plantilla y mes (guardado como el dia 1).
     *
     * <p>La columna es {@code applied_month} y no {@code month} porque MONTH es palabra
     * reservada en SQL, igual que USER en la tabla de usuarios.</p>
     */
    @Embeddable
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "fixed_expense_id", nullable = false)
        private UUID fixedExpenseId;

        @Column(name = "applied_month", nullable = false)
        private LocalDate appliedMonth;

        protected Key() {
            // Requerido por JPA.
        }

        public Key(UUID fixedExpenseId, LocalDate appliedMonth) {
            this.fixedExpenseId = fixedExpenseId;
            this.appliedMonth = appliedMonth;
        }

        public UUID getFixedExpenseId() {
            return fixedExpenseId;
        }

        public LocalDate getAppliedMonth() {
            return appliedMonth;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(fixedExpenseId, key.fixedExpenseId)
                    && Objects.equals(appliedMonth, key.appliedMonth);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fixedExpenseId, appliedMonth);
        }
    }
}
