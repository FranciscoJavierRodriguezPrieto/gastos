package com.gastos.expenses.infrastructure.persistence.jpa;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de las marcas de "ya generado". */
public interface FixedExpenseApplicationJpaRepository
        extends JpaRepository<FixedExpenseApplicationEntity, FixedExpenseApplicationEntity.Key> {

    /**
     * Plantillas del hogar ya expandidas en ese mes.
     *
     * <p>Se cruza con {@code fixed_expense} para acotar por hogar: la tabla de marcas no
     * lleva el hogar, porque ya lo lleva la plantilla a la que apunta.</p>
     */
    @Query("""
            select a.id.fixedExpenseId
              from FixedExpenseApplicationEntity a, FixedExpenseEntity f
             where a.id.fixedExpenseId = f.id
               and f.householdId = :householdId
               and a.id.appliedMonth = :month
            """)
    List<UUID> findAppliedIn(@Param("householdId") UUID householdId,
                             @Param("month") LocalDate month);

    /**
     * Reserva el mes si nadie lo tenia. Devuelve 1 si la reserva es nuestra y 0 si ya
     * estaba cogida.
     *
     * <p>Es SQL nativo y no {@code save()} por un motivo concreto: la clave de esta tabla
     * es <strong>asignada</strong>, asi que Spring Data no la considera nueva y llama a
     * {@code merge()}, que ante una fila existente hace un UPDATE inocuo y devuelve exito.
     * Es decir, {@code save()} nunca diria que el mes estaba cogido.</p>
     *
     * <p>El {@code where not exists} va dentro de la misma sentencia a proposito: entre
     * un {@code exists} suelto y un {@code insert} cabe la otra peticion. Aun asi, en una
     * carrera muy justa puede saltar la clave primaria, y por eso el adaptador sigue
     * capturandola.</p>
     *
     * <p>SQL estandar, sin {@code ON CONFLICT} ni {@code MERGE}: funciona igual en
     * PostgreSQL y en el H2 de los tests, que es la regla del esquema de este proyecto.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into fixed_expense_application (fixed_expense_id, applied_month)
            select :fixedExpenseId, :month
             where not exists (
                   select 1
                     from fixed_expense_application
                    where fixed_expense_id = :fixedExpenseId
                      and applied_month = :month)
            """, nativeQuery = true)
    int claim(@Param("fixedExpenseId") UUID fixedExpenseId, @Param("month") LocalDate month);
}
