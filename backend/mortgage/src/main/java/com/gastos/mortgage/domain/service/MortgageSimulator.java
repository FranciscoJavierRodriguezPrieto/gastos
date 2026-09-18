package com.gastos.mortgage.domain.service;

import com.gastos.mortgage.domain.model.AmortizationSchedule;
import com.gastos.mortgage.domain.model.FinancingPlan;
import com.gastos.mortgage.domain.model.LoanTerms;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.model.UpfrontCosts;
import com.gastos.mortgage.domain.model.ViabilityAssessment;
import com.gastos.mortgage.domain.policy.LendingPolicy;
import com.gastos.mortgage.domain.policy.MiPrimeraViviendaPolicy;
import com.gastos.mortgage.domain.policy.PurchaseCostsPolicy;
import com.gastos.shared.domain.Guard;
import com.gastos.shared.domain.Money;
import com.gastos.shared.domain.Percentage;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de simulacion hipotecaria. Orquesta, en este orden:
 *
 * <ol>
 *   <li>gastos iniciales no financiables (ITP + notaria, registro y gestoria);</li>
 *   <li>LTV maximo aplicable segun el programa Mi Primera Vivienda;</li>
 *   <li>estructura de financiacion (prestamo, entrada, efectivo necesario);</li>
 *   <li>cuota mensual por amortizacion francesa;</li>
 *   <li>veredicto de viabilidad segun DTI y colchon.</li>
 * </ol>
 *
 * <p>Servicio de dominio sin estado ni anotaciones de framework: se instancia con sus
 * politicas y se puede ejecutar en un test unitario en microsegundos.</p>
 */
public final class MortgageSimulator {

    private final PurchaseCostsPolicy purchaseCostsPolicy;
    private final MiPrimeraViviendaPolicy programPolicy;
    private final ViabilityAnalyzer viabilityAnalyzer;

    public MortgageSimulator(PurchaseCostsPolicy purchaseCostsPolicy,
                             MiPrimeraViviendaPolicy programPolicy,
                             LendingPolicy lendingPolicy) {
        this.purchaseCostsPolicy = Guard.notNull(purchaseCostsPolicy, "purchaseCostsPolicy");
        this.programPolicy = Guard.notNull(programPolicy, "programPolicy");
        this.viabilityAnalyzer = new ViabilityAnalyzer(Guard.notNull(lendingPolicy, "lendingPolicy"));
    }

    /** Configuracion por defecto: Madrid, segunda mano, Mi Primera Vivienda, criterio bancario estandar. */
    public static MortgageSimulator madridDefaults() {
        return new MortgageSimulator(PurchaseCostsPolicy.madridSecondHand(),
                MiPrimeraViviendaPolicy.defaults(),
                LendingPolicy.spanishStandard());
    }

    public SimulationResult simulate(SimulationRequest request) {
        Guard.notNull(request, "request");

        UpfrontCosts upfrontCosts = UpfrontCosts.of(request.propertyPrice(), purchaseCostsPolicy);
        Percentage maxLtv = programPolicy.applicableLoanToValue(request.propertyPrice(),
                request.applicant().age(), request.applicant().firstHome());
        FinancingPlan plan = FinancingPlan.compute(request.propertyPrice(), request.availableSavings(),
                request.targetReserve(), upfrontCosts, maxLtv);

        Money monthlyPayment = Money.zero();
        Money totalInterest = Money.zero();
        if (plan.loanAmount().isPositive()) {
            AmortizationSchedule schedule = AmortizationCalculator.schedule(loanTermsFor(request, plan));
            monthlyPayment = schedule.monthlyPayment();
            totalInterest = schedule.totalInterest();
        }

        ViabilityAssessment viability =
                viabilityAnalyzer.analyze(plan, monthlyPayment, request.applicant());

        return new SimulationResult(request, upfrontCosts, plan, monthlyPayment, totalInterest, viability);
    }

    /** Cuadro de amortizacion del escenario, calculado bajo demanda por su tamano. */
    public AmortizationSchedule scheduleFor(SimulationRequest request) {
        Guard.notNull(request, "request");
        SimulationResult result = simulate(request);
        return AmortizationCalculator.schedule(loanTermsFor(request, result.financingPlan()));
    }

    /**
     * Barrido de escenarios sobre los ingresos del hogar: alimenta el control deslizante
     * que responde a "que pasa si pasamos de 3.400 a 4.000 EUR al mes".
     */
    public List<SimulationResult> sweepByIncome(SimulationRequest base, List<Money> incomes) {
        Guard.notNull(base, "base");
        Guard.notEmpty(incomes, "incomes");
        List<SimulationResult> results = new ArrayList<>(incomes.size());
        for (Money income : incomes) {
            results.add(simulate(base.withNetMonthlyIncome(income)));
        }
        return List.copyOf(results);
    }

    /** Barrido sobre el tipo de interes: sensibilidad de la cuota ante subidas del TIN. */
    public List<SimulationResult> sweepByRate(SimulationRequest base, List<Percentage> rates) {
        Guard.notNull(base, "base");
        Guard.notEmpty(rates, "rates");
        List<SimulationResult> results = new ArrayList<>(rates.size());
        for (Percentage rate : rates) {
            results.add(simulate(base.withAnnualNominalRate(rate)));
        }
        return List.copyOf(results);
    }

    private static LoanTerms loanTermsFor(SimulationRequest request, FinancingPlan plan) {
        return new LoanTerms(plan.loanAmount(), request.annualNominalRate(), request.termYears());
    }
}
