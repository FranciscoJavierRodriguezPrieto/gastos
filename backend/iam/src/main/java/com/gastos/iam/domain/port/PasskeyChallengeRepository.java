package com.gastos.iam.domain.port;

import com.gastos.iam.domain.model.PasskeyChallenge;
import java.time.Instant;
import java.util.Optional;

/** Puerto de salida de los retos WebAuthn. */
public interface PasskeyChallengeRepository {

    Optional<PasskeyChallenge> findByChallenge(String challenge);

    PasskeyChallenge save(PasskeyChallenge challenge);

    /**
     * Borra los retos caducados.
     *
     * <p>A diferencia de los tokens de refresco, un reto consumido o caducado no tiene
     * ningun valor forense: no se invalida, se tira. Sin esto la tabla crece sin fin con
     * ceremonias que nadie llego a terminar.</p>
     */
    int deleteExpired(Instant now);
}
