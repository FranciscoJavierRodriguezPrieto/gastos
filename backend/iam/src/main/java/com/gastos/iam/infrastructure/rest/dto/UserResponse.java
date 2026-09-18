package com.gastos.iam.infrastructure.rest.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Usuario. Nunca incluye hash, ni token, ni nada derivado de la contrasena. */
public record UserResponse(UUID id,
                           UUID householdId,
                           String email,
                           String displayName,
                           String role,
                           BigDecimal monthlyNetIncome) {
}
