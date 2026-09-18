package com.gastos.iam.infrastructure.rest;

import com.gastos.iam.application.AuthenticationResult;
import com.gastos.iam.domain.model.User;
import com.gastos.iam.infrastructure.rest.dto.TokenResponse;
import com.gastos.iam.infrastructure.rest.dto.UserResponse;
import java.util.List;

/** Traduce entre el contrato HTTP de identidad y el dominio. */
public final class AuthRestMapper {

    private static final String BEARER = "Bearer";

    private AuthRestMapper() {
    }

    public static TokenResponse toResponse(AuthenticationResult result) {
        return new TokenResponse(
                result.accessToken(),
                result.refreshToken(),
                BEARER,
                result.expiresInSeconds(),
                // El usuario del resultado solo trae identidad y rol; el resto de sus
                // datos se consulta aparte y no viaja en la respuesta del login.
                new UserResponse(
                        result.user().userId().value(),
                        result.user().householdId().value(),
                        null,
                        null,
                        result.user().role(),
                        null));
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.id().value(),
                user.householdId().value(),
                user.email().value(),
                user.displayName(),
                user.role().name(),
                user.monthlyNetIncome().amount());
    }

    public static List<UserResponse> toResponses(List<User> users) {
        return users.stream().map(AuthRestMapper::toResponse).toList();
    }
}
