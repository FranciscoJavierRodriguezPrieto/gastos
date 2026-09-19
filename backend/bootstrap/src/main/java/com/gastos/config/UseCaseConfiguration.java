package com.gastos.config;

import com.gastos.accounts.application.ManageAccountsUseCase;
import com.gastos.accounts.domain.port.AccountRepository;
import com.gastos.expenses.application.ManageExpensesUseCase;
import com.gastos.iam.application.AuthenticateUseCase;
import com.gastos.iam.application.ManageHouseholdUseCase;
import com.gastos.iam.application.PasskeyUseCase;
import com.gastos.iam.application.RecoverAccessUseCase;
import com.gastos.iam.application.port.ResetTokenService;
import com.gastos.iam.application.port.WebAuthnCeremony;
import com.gastos.iam.application.port.TokenService;
import com.gastos.iam.domain.port.EmailSender;
import com.gastos.iam.domain.port.HouseholdRepository;
import com.gastos.iam.domain.port.PasskeyChallengeRepository;
import com.gastos.iam.domain.port.PasskeyCredentialRepository;
import com.gastos.iam.domain.port.PasswordResetTokenRepository;
import com.gastos.iam.domain.port.PasswordHasher;
import com.gastos.iam.domain.port.RefreshTokenRepository;
import com.gastos.iam.domain.port.UserCredentialRepository;
import com.gastos.iam.domain.port.UserRepository;
import com.gastos.expenses.domain.port.ExpenseRepository;
import com.gastos.mortgage.application.ManageAidProgramsUseCase;
import com.gastos.mortgage.application.SimulateMortgageUseCase;
import com.gastos.mortgage.domain.port.AidProgramRepository;
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
                                                           MortgageScenarioRepository scenarios,
                                                           AidProgramRepository programs,
                                                           Clock clock) {
        return new SimulateMortgageUseCase(simulator, scenarios, programs, clock);
    }

    @Bean
    public ManageAidProgramsUseCase manageAidProgramsUseCase(AidProgramRepository repository) {
        return new ManageAidProgramsUseCase(repository);
    }

    @Bean
    public AuthenticateUseCase authenticateUseCase(UserRepository users,
                                                   UserCredentialRepository credentials,
                                                   RefreshTokenRepository refreshTokens,
                                                   PasswordHasher passwordHasher,
                                                   TokenService tokenService,
                                                   Clock clock) {
        return new AuthenticateUseCase(users, credentials, refreshTokens, passwordHasher,
                tokenService, clock);
    }

    @Bean
    public RecoverAccessUseCase recoverAccessUseCase(UserRepository users,
                                                     UserCredentialRepository credentials,
                                                     PasswordResetTokenRepository resetTokens,
                                                     RefreshTokenRepository refreshTokens,
                                                     PasswordHasher passwordHasher,
                                                     ResetTokenService resetTokenService,
                                                     EmailSender emailSender,
                                                     Clock clock) {
        return new RecoverAccessUseCase(users, credentials, resetTokens, refreshTokens,
                passwordHasher, resetTokenService, emailSender, clock);
    }

    @Bean
    public PasskeyUseCase passkeyUseCase(UserRepository users,
                                         PasskeyCredentialRepository credentials,
                                         PasskeyChallengeRepository challenges,
                                         WebAuthnCeremony ceremony,
                                         AuthenticateUseCase authenticate,
                                         Clock clock) {
        return new PasskeyUseCase(users, credentials, challenges, ceremony, authenticate, clock);
    }

    @Bean
    public ManageHouseholdUseCase manageHouseholdUseCase(HouseholdRepository households,
                                                         UserRepository users,
                                                         UserCredentialRepository credentials,
                                                         PasswordHasher passwordHasher,
                                                         AuthenticateUseCase authenticate,
                                                         Clock clock) {
        return new ManageHouseholdUseCase(households, users, credentials, passwordHasher,
                authenticate, clock);
    }
}
