package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangeResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo fluxo de downgrade no contexto do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar elegibilidade do downgrade.</li>
 *   <li>Aplicar o downgrade imediato.</li>
 *   <li>Montar a resposta consolidada.</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem ApiException com status hardcoded.</li>
 *   <li>Sem alterar regra de negócio.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountPlanDowngradeService {

    private final AccountPlanChangeService accountPlanChangeService;

    public AccountPlanChangeResponse handleDowngrade(
            ChangeAccountPlanCommand command,
            PlanEligibilityResult preview
    ) {
        if (!preview.eligible()) {

            log.warn(
                    "Downgrade rejeitado por inelegibilidade. accountId={}, currentPlan={}, targetPlan={}, changeType={}",
                    command.accountId(),
                    preview.currentPlan(),
                    preview.targetPlan(),
                    preview.changeType()
            );

            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Downgrade não elegível para a conta atual"
            );
        }

        AccountPlanChangeResult result = accountPlanChangeService.applyEligibleDowngrade(command);

        log.info(
                "Downgrade aplicado com sucesso no control plane. accountId={}, oldPlan={}, newPlan={}, changeType={}",
                result.accountId(),
                result.oldPlan(),
                result.newPlan(),
                result.changeType()
        );

        return new AccountPlanChangeResponse(
                result.accountId(),
                result.oldPlan().name(),
                result.newPlan().name(),
                result.newPlan().name(),
                result.changeType().name(),
                result.eligibility().eligible(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Downgrade aplicado com sucesso."
        );
    }
}