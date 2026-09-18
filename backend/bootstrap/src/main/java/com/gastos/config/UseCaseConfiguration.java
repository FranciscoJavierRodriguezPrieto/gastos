package com.gastos.config;

import com.gastos.accounts.application.ManageAccountsUseCase;
import com.gastos.accounts.domain.port.AccountRepository;
import com.gastos.expenses.application.ManageExpensesUseCase;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.mortgage.application.SimulateMortgageUseCase;
import com.gastos.mortgage.domain.port.MortgageScenarioRepository;
import com.gastos.mortgage.domain.service.MortgageSimulator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Raiz de composicion de los casos de uso.
 *
 * <p>Las clases de {@code application} no llevan anotaciones de Spring: se construyen
 * aqui a mano. El precio es este fichero; el beneficio es que un caso de uso se puede
 * instanciar en un test con un doble del repositorio y sin contexto de aplicacion.</p>
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    public ManageExpensesUseCase manageExpensesUseCase(ExpenseRepository repository) {
        return new ManageExpensesUseCase(repository);
    }

    @Bean
    public ManageAccountsUseCase manageAccountsUseCase(AccountRepository repository, Clock clock) {
        return new ManageAccountsUseCase(repository, clock);
    }

    @Bean
    public SimulateMortgageUseCase simulateMortgageUseCase(MortgageSimulator simulator,
                                                           MortgageScenarioRepository repository,
                                                           Clock clock) {
        return new SimulateMortgageUseCase(simulator, repository, clock);
    }
}
