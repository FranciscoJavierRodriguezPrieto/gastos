package com.gastos.shared.domain;

/**
 * Error de regla de negocio. Se traduce en la capa de infraestructura a un
 * 422/400 sin filtrar detalles internos (OWASP A09 - fallos de logging y
 * exposicion de informacion).
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
