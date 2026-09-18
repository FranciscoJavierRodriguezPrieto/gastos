package com.gastos.mortgage.domain.policy;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.HouseholdId;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.List;

/**
 * Catalogo de partida que el hogar puede instalar y despues editar.
 *
 * <p><strong>Estas cifras no estan verificadas.</strong> Son un punto de partida para no
 * empezar con una pantalla en blanco, no una fuente normativa. Antes de tomar ninguna
 * decision real hay que contrastarlas con la convocatoria vigente publicada por la
 * Comunidad de Madrid, y corregirlas desde la propia aplicacion.</p>
 *
 * <p>Por eso las plantillas de las que no se conocen las condiciones exactas se instalan
 * <strong>desactivadas</strong>: una cifra inventada que participa en el calculo sin que
 * nadie la haya mirado es peor que no tener el programa.</p>
 */
public final class ReferenceAidPrograms {

    private ReferenceAidPrograms() {
    }

    public static List<AidProgram> installFor(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return List.of(
                AidProgram.create(
                        householdId,
                        "Mi Primera Vivienda (Comunidad de Madrid)",
                        Percentage.of("95.00"),
                        Money.euros(390_000),
                        35,
                        true,
                        true,
                        "Valores aproximados de la convocatoria. Verificar LTV avalado, precio "
                                + "maximo del inmueble y edad en el BOCM antes de usarlos."),
                AidProgram.create(
                        householdId,
                        "Aval hasta el 100% para menores de 40 (por verificar)",
                        Percentage.of("100.00"),
                        null,
                        40,
                        true,
                        false,
                        "PLANTILLA SIN VERIFICAR. Se instala desactivada a proposito: confirma "
                                + "que la ayuda existe y cuales son su LTV, edad, limite de renta y "
                                + "precio maximo antes de activarla."));
    }
}
