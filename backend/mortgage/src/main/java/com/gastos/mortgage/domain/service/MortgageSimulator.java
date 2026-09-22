package com.gastos.mortgage.domain.service;

import com.gastos.mortgage.domain.model.AidProgram;
import com.gastos.mortgage.domain.model.AmortizationSchedule;
import com.gastos.mortgage.domain.model.FinancingDecision;
import com.gastos.mortgage.domain.model.FinancingPlan;
import com.gastos.mortgage.domain.model.LoanTerms;
import com.gastos.mortgage.domain.model.SimulationRequest;
import com.gastos.mortgage.domain.model.SimulationResult;
import com.gastos.mortgage.domain.model.UpfrontCosts;
import com.gastos.mortgage.domain.model.ViabilityAssessment;
import com.gastos.mortgage.domain.policy.LendingPolicy;
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
 *   <li>LTV maximo aplicable, segun el modo de financiacion elegido;</li>
 *   <li>estructura de financiacion (prestamo, entrada, efectivo necesario);</li>
 *   <li>cuota mensual por amortizacion francesa;</li>
 *   <li>veredicto de viabilidad segun DTI y colchon.</li>
 * </ol>
 *
 * <p>Solo el paso 2 depende de los programas de ayuda. Del 3 en adelante el motor
 * trabaja con un LTV y le da igual si viene de una convocatoria autonomica, de la
 * financiacion estandar o de un valor que el usuario escribio a mano.</p>
 *
 * <p>Los programas se reciben como parametro en lugar de consultarse a un repositorio:
 * asi el servicio de dominio sigue sin estado ni dependencias, y un test puede pasarle
 * el catalogo que quiera sin base de datos.</p>
 */
public final class MortgageSimulator {

    private final PurchaseCostsPolicy purchaseCostsPolicy;
    private final LendingPolicy lendingPolicy;
    private final FinancingSelector financingSelector;
    private final ViabilityAnalyzer viabilityAnalyzer;

    public MortgageSimulator(PurchaseCostsPolicy purchaseCostsPolicy, LendingPolicy lendingPolicy) {
        this.purchaseCostsPolicy = Guard.notNull(purchaseCostsPolicy, "purchaseCostsPolicy");
        this.lendingPolicy = Guard.notNull(lendingPolicy, "lendingPolicy");
        this.financingSelector = new FinancingSelector(lendingPolicy.standardLoanToValue());
        this.viabilityAnalyzer = new ViabilityAnalyzer(lendingPolicy);
    }

    /** Configuracion por defecto: Madrid, segunda mano, criterio bancario estandar. */
    public static MortgageSimulator madridDefaults() {
        return new MortgageSimulator(PurchaseCostsPolicy.madridSecondHand(),
                LendingPolicy.spanishStandard());
    }

    /** Simulacion sin programas de ayuda: financiacion estandar. */
    public SimulationResult simulate(SimulationRequest request) {
        return simulate(request, List.of());
    }

    public SimulationResult simulate(SimulationRequest request, List<AidProgram> programs) {
        Guard.notNull(request, "request");
        Guard.notNull(programs, "programs");

        UpfrontCosts upfrontCosts = UpfrontCosts.of(request.propertyPrice(), purchaseCostsPolicy,
                request.applicant());

        FinancingDecision decision = financingSelector.decide(
                request.financing(), request.propertyPrice(), request.applicant(), programs);

        FinancingPlan plan = FinancingPlan.compute(request.propertyPrice(), request.availableSavings(),
                request.targetReserve(), upfrontCosts, decision.appliedLoanToValue());

        Money monthlyPayment = Money.zero();
        Money totalInterest = Money.zero();
        if (plan.loanAmount().isPositive()) {
            AmortizationSchedule schedule = AmortizationCalculator.schedule(loanTermsFor(request, plan));
            monthlyPayment = schedule.monthlyPayment();
            totalInterest = schedule.totalInterest();
        }

        ViabilityAssessment viability =
                viabilityAnalyzer.analyze(plan, monthlyPayment, request.applicant(), decision);

        return new SimulationResult(request, upfrontCosts, plan, monthlyPayment, totalInterest,
                decision, viability);
    }

    /** Cuadro de amortizacion del escenario, calculado bajo demanda por su tamano. */
    public AmortizationSchedule scheduleFor(SimulationRequest request, List<AidProgram> programs) {
        SimulationResult result = simulate(request, programs);
        return AmortizationCalculator.schedule(loanTermsFor(request, result.financingPlan()));
    }

    /**
     * Barrido de escenarios sobre los ingresos del hogar: alimenta el control deslizante
     * que responde a "que pasa si pasamos de 3.400 a 4.000 EUR al mes".
     */
    public List<SimulationResult> sweepByIncome(SimulationRequest base, List<Money> incomes,
                                                List<AidProgram> programs) {
        Guard.notNull(base, "base");
        Guard.notEmpty(incomes, "incomes");
        List<SimulationResult> results = new ArrayList<>(incomes.size());
        for (Money income : incomes) {
            results.add(simulate(base.withNetMonthlyIncome(income), programs));
        }
        return List.copyOf(results);
    }

    /** Barrido sobre el tipo de interes: sensibilidad de la cuota ante subidas del TIN. */
    public List<SimulationResult> sweepByRate(SimulationRequest base, List<Percentage> rates,
                                              List<AidProgram> programs) {
        Guard.notNull(base, "base");
        Guard.notEmpty(rates, "rates");
        List<SimulationResult> results = new ArrayList<>(rates.size());
        for (Percentage rate : rates) {
            results.add(simulate(base.withAnnualNominalRate(rate), programs));
        }
        return List.copyOf(results);
    }

    public LendingPolicy lendingPolicy() {
        return lendingPolicy;
    }

    private static LoanTerms loanTermsFor(SimulationRequest request, FinancingPlan plan) {
        return new LoanTerms(plan.loanAmount(), request.annualNominalRate(), request.termYears());
    }
}
