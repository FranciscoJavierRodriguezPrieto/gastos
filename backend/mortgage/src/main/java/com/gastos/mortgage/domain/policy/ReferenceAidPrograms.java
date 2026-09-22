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
 * <p>Fuente: <strong>Orden de 27 de julio de 2026</strong> de la Consejeria de Vivienda,
 * Transportes e Infraestructuras, por la que se regula el Programa Mi Primera Vivienda
 * (BOCM num. 186, de 6 de agosto de 2026; en vigor desde el 7 de agosto). Deroga la
 * Orden 2350/2022, que es la que fijaba el 95%, los 390.000 € y los 35 anos.</p>
 *
 * <p>El programa tiene cuatro vias de acceso con distinta financiacion maxima, y cada
 * una se instala como un programa aparte. Con el modo automatico, el simulador aplica el
 * que mas financie de entre los que se cumplen, que es exactamente el tramo que le toca
 * al hogar: a los 42 anos se cumplen el de 45 y el de 50, y gana el de 45 con su 95%.</p>
 *
 * <p>Requisitos del programa que el simulador NO comprueba, porque no tiene los datos:
 * residencia legal en la Comunidad de Madrid durante los dos anos anteriores a la
 * solicitud, que todas las personas adquirentes sean mayores de edad, y destinar la
 * vivienda a residencia habitual durante al menos cinco anos. La financiacion se calcula
 * sobre el menor entre precio y tasacion; aqui, sobre el precio.</p>
 */
public final class ReferenceAidPrograms {

    /** Articulo 4.b de la Orden: precio sin gastos ni tributos. */
    private static final Money PRECIO_MAXIMO = Money.euros(425_000);

    /*
     * Estas cadenas SE VEN EN PANTALLA: son el nombre y la nota de origen de cada
     * programa, tal cual, en la lista y en el veredicto del simulador. Por eso van con
     * tildes y con enes, al reves que los comentarios de este modulo. El proyecto compila
     * en UTF-8 (project.build.sourceEncoding), asi que no hay nada que temer.
     */
    private static final String FUENTE = "Orden 27/07/2026, BOCM 186 de 06/08/2026, en vigor el "
            + "07/08/2026. Exige además 2 años de residencia en Madrid y vivir en ella 5 años.";

    private ReferenceAidPrograms() {
    }

    public static List<AidProgram> installFor(HouseholdId householdId) {
        Guard.notNull(householdId, "householdId");
        return List.of(
                // Articulo 2.a: "personas que no superen los cuarenta anos".
                tramo(householdId, "Mi Primera Vivienda: hasta 40 años", "100.00", 40),
                // Articulo 2.b.
                tramo(householdId, "Mi Primera Vivienda: hasta 45 años", "95.00", 45),
                // Articulo 2.c.
                tramo(householdId, "Mi Primera Vivienda: hasta 50 años", "90.00", 50),
                // Articulo 2.a y 3.a: al 100% y "en estos casos sin limite de edad".
                AidProgram.create(
                        householdId,
                        "Mi Primera Vivienda: familias con hijos",
                        Percentage.of("100.00"),
                        PRECIO_MAXIMO,
                        null,
                        true,
                        true,
                        true,
                        "Familias numerosas, monoparentales o con hijos menores a cargo, sin "
                                + "límite de edad. " + FUENTE));
    }

    private static AidProgram tramo(HouseholdId householdId, String nombre, String ltv,
                                    int edadMaxima) {
        return AidProgram.create(householdId, nombre, Percentage.of(ltv), PRECIO_MAXIMO, edadMaxima,
                true, false, true, FUENTE);
    }
}
