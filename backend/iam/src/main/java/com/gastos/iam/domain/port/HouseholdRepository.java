package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.Household;
import com.gastos.shared.domain.HouseholdId;
import java.util.Optional;

/** Puerto de salida del hogar. */
public interface HouseholdRepository {

    Optional<Household> findById(HouseholdId householdId);

    /** El alta inicial solo se permite si no existe ningun hogar: la instancia es de un
     *  unico hogar y no admite registro abierto. */
    boolean existsAny();

    Household save(Household household);
}
