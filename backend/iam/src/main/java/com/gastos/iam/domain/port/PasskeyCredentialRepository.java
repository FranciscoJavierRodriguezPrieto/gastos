package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.PasskeyCredential;
import com.gastos.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida de las passkeys. */
public interface PasskeyCredentialRepository {

    /**
     * Busca por el identificador que asigna el autenticador.
     *
     * <p>Es la unica consulta que no se acota por usuario, y no puede serlo: al entrar
     * con passkey el usuario se descubre precisamente a partir de la credencial.</p>
     */
    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    Optional<PasskeyCredential> findById(UUID id);

    List<PasskeyCredential> findAllByUser(UserId userId);

    PasskeyCredential save(PasskeyCredential credential);

    void delete(UUID id);
}
