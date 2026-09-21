package com.gastos.expenses.infrastructure.persistence.jpa;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva un mes para un gasto fijo, en una transaccion propia y desechable.
 *
 * <h2>Por que esto es un bean aparte y no un metodo del adaptador</h2>
 *
 * <p>Porque <strong>capturar la violacion de clave dentro de la transaccion que la
 * provoca no sirve de nada</strong>. Al saltar, la transaccion queda marcada como "solo
 * deshacer"; por mucho que se atrape la excepcion y se devuelva un valor normal, al
 * intentar confirmarla el proxy lanza {@code UnexpectedRollbackException} y el error
 * reaparece, ahora disfrazado y mas dificil de leer.</p>
 *
 * <p>La captura tiene que estar <em>fuera</em> del limite transaccional, y un limite
 * transaccional solo existe cuando la llamada cruza el proxy de Spring: de ahi que sea
 * una clase distinta y no un metodo privado. Aqui se pierde la transaccion; quien llama,
 * desde fuera, recoge el fracaso y sigue.</p>
 *
 * <p>No es paranoia: la pantalla de gastos pide el listado y el resumen <strong>a la
 * vez</strong>, y los dos expanden el mes. Esta carrera ocurre en cada carga.</p>
 */
@Component
class FixedExpenseMonthClaimer {

    private final FixedExpenseApplicationJpaRepository applications;

    FixedExpenseMonthClaimer(FixedExpenseApplicationJpaRepository applications) {
        this.applications = applications;
    }

    /**
     * @return 1 si la reserva es nuestra, 0 si ya estaba cogida
     * @throws org.springframework.dao.DataIntegrityViolationException si dos peticiones
     *         insertan a la vez; lo gestiona quien llama
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    int tryClaim(UUID fixedExpenseId, LocalDate month) {
        return applications.claim(fixedExpenseId, month);
    }
}
