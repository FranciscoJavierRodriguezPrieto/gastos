package com.gastos.mortgage.domain.model;

import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import java.util.List;

/**
 * Resultado del analisis de viabilidad: veredicto, ratios y los motivos concretos que
 * lo justifican.
 *
 * <p>Los motivos se devuelven explicitos para que la interfaz no tenga que reconstruir
 * el razonamiento: el dominio explica por que una operacion no sale, y la UI solo la
 * pinta.</p>
 */
public record ViabilityAssessment(ViabilityVerdict verdict,
                                  DebtToIncomeRatio debtToIncome,
                                  Money monthlyPayment,
                                  List<String> blockingReasons,
                                  List<String> warnings) {

    public ViabilityAssessment {
        Guard.notNull(verdict, "verdict");
        Guard.notNull(debtToIncome, "debtToIncome");
        Guard.notNull(monthlyPayment, "monthlyPayment");
        blockingReasons = List.copyOf(Guard.notNull(blockingReasons, "blockingReasons"));
        warnings = List.copyOf(Guard.notNull(warnings, "warnings"));
    }

    public boolean isViable() {
        return verdict != ViabilityVerdict.INVIABLE;
    }
}
