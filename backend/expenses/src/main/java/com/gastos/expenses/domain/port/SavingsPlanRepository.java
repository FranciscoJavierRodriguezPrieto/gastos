package com.gastos.expenses.domain.port;

import com.gastos.shared.domain.HouseholdId;
import java.util.Optional;

/**
 * Puerto de salida de la prevision de ahorro. Un plan por hogar.
 *
 * <p>El plan viaja como documento JSON ya validado en la frontera REST: el dominio no lo
 * interpreta, solo lo guarda. La prevision se calcula en el cliente a partir de el.</p>
 */
public interface SavingsPlanRepository {

    Optional<String> find(HouseholdId householdId);

    void save(HouseholdId householdId, String document);
}
