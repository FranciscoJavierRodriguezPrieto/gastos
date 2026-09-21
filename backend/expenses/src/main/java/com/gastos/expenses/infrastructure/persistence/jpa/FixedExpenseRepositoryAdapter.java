package com.gastos.expenses.infrastructure.persistence.jpa;

import com.gastos.expenses.domain.model.FixedExpense;
import com.gastos.expenses.domain.model.FixedExpenseId;
import com.gastos.expenses.domain.port.FixedExpenseRepository;
import com.gastos.shared.domain.HouseholdId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de salida de los gastos fijos sobre Spring Data JPA. */
@Repository
@Transactional
public class FixedExpenseRepositoryAdapter implements FixedExpenseRepository {

    private final FixedExpenseJpaRepository fixedExpenses;
    private final FixedExpenseApplicationJpaRepository applications;
    private final FixedExpenseMonthClaimer claimer;

    public FixedExpenseRepositoryAdapter(FixedExpenseJpaRepository fixedExpenses,
                                         FixedExpenseApplicationJpaRepository applications,
                                         FixedExpenseMonthClaimer claimer) {
        this.fixedExpenses = fixedExpenses;
        this.applications = applications;
        this.claimer = claimer;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FixedExpense> findById(HouseholdId householdId, FixedExpenseId fixedExpenseId) {
        return fixedExpenses.findByIdAndHouseholdId(fixedExpenseId.value(), householdId.value())
                .map(FixedExpenseJpaMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FixedExpense> findAll(HouseholdId householdId) {
        return fixedExpenses.findByHouseholdIdOrderByDescriptionAsc(householdId.value()).stream()
                .map(FixedExpenseJpaMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<FixedExpenseId> findAppliedIn(HouseholdId householdId, YearMonth month) {
        return applications.findAppliedIn(householdId.value(), FixedExpenseJpaMapper.toColumn(month))
                .stream()
                .map(FixedExpenseId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Perder la carrera es normal y no puede tumbar la peticion.
     *
     * <p>{@code NOT_SUPPORTED} suspende cualquier transaccion en curso, de modo que este
     * metodo <strong>no esta dentro de ninguna</strong>. Es lo que hace que el
     * {@code catch} sirva: la transaccion que revienta es la de
     * {@link FixedExpenseMonthClaimer}, vive y muere dentro de esa llamada, y aqui solo
     * llega la excepcion. Capturarla sin salir antes del limite transaccional no
     * funcionaba: la transaccion quedaba marcada para deshacerse y el error volvia al
     * confirmarla.</p>
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean claimFor(FixedExpenseId fixedExpenseId, YearMonth month) {
        try {
            return claimer.tryClaim(fixedExpenseId.value(),
                    FixedExpenseJpaMapper.toColumn(month)) == 1;
        } catch (DataIntegrityViolationException | UnexpectedRollbackException yaEstaba) {
            // Otra peticion inserto entre nuestra comprobacion y nuestro insert. El
            // resultado es el mismo: el mes no es nuestro y no generamos nada.
            return false;
        }
    }

    @Override
    public FixedExpense save(FixedExpense fixedExpense) {
        fixedExpenses.save(FixedExpenseJpaMapper.toEntity(fixedExpense));
        return fixedExpense;
    }

    @Override
    public void delete(HouseholdId householdId, FixedExpenseId fixedExpenseId) {
        // Las marcas caen con la plantilla (ON DELETE CASCADE). Los gastos que genero NO:
        // la clave ajena es ON DELETE SET NULL, asi que se quedan como gastos sueltos.
        fixedExpenses.deleteByIdAndHouseholdId(fixedExpenseId.value(), householdId.value());
    }
}
