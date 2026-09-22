package com.gastos.shared.domain;

/**
 * El recurso no existe, o existe pero pertenece a otro hogar.
 *
 * <p>Los dos casos comparten excepcion y mensaje a proposito: distinguirlos permitiria
 * a un atacante enumerar identificadores ajenos comparando respuestas (OWASP API1,
 * BOLA/IDOR). El adaptador REST la traduce siempre a 404.</p>
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
