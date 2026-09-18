package com.gastos.accounts.application;

import com.gastos.accounts.domain.model.AccountType;
import com.gastos.accounts.domain.model.Ownership;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.UserId;
import java.util.Set;

/** Orden de alta de una cuenta, ya en tipos de dominio. */
public record OpenAccountCommand(HouseholdId householdId,
                                 String alias,
                                 String bankName,
                                 AccountType type,
                                 Ownership ownership,
                                 Set<UserId> holders,
                                 Money initialBalance) {
}
