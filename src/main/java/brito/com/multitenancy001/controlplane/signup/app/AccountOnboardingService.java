package brito.com.multitenancy001.controlplane.signup.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada principal do onboarding de Account no Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Manter compatibilidade com controllers e chamadores atuais.</li>
 *   <li>Delegar o fluxo real de onboarding para o command service.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Esta classe deve permanecer fina.</li>
 *   <li>Não deve concentrar validação, provisionamento, auditoria
 *       ou sincronização de identidade.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountOnboardingService {

    private final AccountOnboardingCommandService accountOnboardingCommandService;

    /**
     * Executa o fluxo de criação e provisionamento de uma account.
     *
     * @param signupCommand comando de signup
     * @return resultado do onboarding
     */
    public SignupResult createAccount(SignupCommand signupCommand) {
        log.info("Delegando createAccount para accountOnboardingCommandService.");
        return accountOnboardingCommandService.createAccount(signupCommand);
    }
}