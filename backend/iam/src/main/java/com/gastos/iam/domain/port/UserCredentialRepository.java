package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.UserCredential;
import com.gastos.shared.domain.UserId;
import java.util.Optional;

/** Puerto de salida de credenciales. */
public interface UserCredentialRepository {

    Optional<UserCredential> findByUserId(UserId userId);

    UserCredential save(UserCredential credential);
}
