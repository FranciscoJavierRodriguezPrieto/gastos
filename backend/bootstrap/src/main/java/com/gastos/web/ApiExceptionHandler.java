package com.gastos.web;

import com.gastos.shared.domain.DomainException;
import com.gastos.shared.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Traduce excepciones a respuestas HTTP.
 *
 * <p>Criterio de reparto:</p>
 * <ul>
 *   <li><strong>400</strong>: la peticion esta mal formada (falta un campo, el tipo no
 *       encaja, falta una cabecera). El cliente puede corregirla.</li>
 *   <li><strong>404</strong>: el recurso no existe <em>o no es de este hogar</em>. Los
 *       dos casos responden igual para no permitir enumerar identificadores ajenos.</li>
 *   <li><strong>422</strong>: la peticion es valida pero una regla de negocio la
 *       rechaza (cuenta conjunta con un solo titular, cargo en descubierto).</li>
 *   <li><strong>500</strong>: cualquier otra cosa. El detalle se queda en el log del
 *       servidor; al cliente solo le llega un mensaje generico.</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", e.getMessage(), request.getRequestURI()));
    }

    /**
     * Regla de negocio incumplida. El mensaje de {@link DomainException} esta redactado
     * para el usuario y no revela nada interno, por eso si se devuelve.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "BUSINESS_RULE_VIOLATION", e.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e,
                                                     HttpServletRequest request) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDATION_ERROR", "La peticion contiene campos no validos",
                        request.getRequestURI(), details));
    }

    @ExceptionHandler({
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception e, HttpServletRequest request) {
        // El mensaje de Jackson puede incluir fragmentos del cuerpo enviado; se registra
        // pero no se devuelve.
        log.debug("Peticion mal formada en {}: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "BAD_REQUEST", "La peticion no tiene el formato esperado",
                        request.getRequestURI()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException e,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND", "Recurso no encontrado", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR", "Se ha producido un error inesperado",
                        request.getRequestURI()));
    }
}
