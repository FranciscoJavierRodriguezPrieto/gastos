package com.gastos.accounts.domain.port;

import com.gastos.accounts.domain.model.Account;
import com.gastos.accounts.domain.model.AccountId;
import com.gastos.shared.domain.HouseholdId;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida del contexto de cuentas. El dominio declara lo que necesita y la
 * infraestructura (JPA/PostgreSQL) implementa el adaptador: invertir la dependencia
 * permite testear la logica financiera sin base de datos.
 *
 * <p>Toda busqueda exige {@link HouseholdId}: el aislamiento multi-tenant se impone
 * en la firma del puerto, no en el criterio del programador de turno.</p>
 */
public interface AccountRepository {

    Optional<Account> findById(HouseholdId householdId, AccountId accountId);

    List<Account> findAllByHousehold(HouseholdId householdId);

    Account save(Account account);

    void delete(HouseholdId householdId, AccountId accountId);
}
