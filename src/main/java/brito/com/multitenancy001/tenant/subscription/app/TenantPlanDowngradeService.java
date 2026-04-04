package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeResult;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.ChangeAccountPlanCommand;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo fluxo de downgrade no self-service do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar elegibilidade do downgrade.</li>
 *   <li>Executar crossing explícito tenant → public.</li>
 *   <li>Aplicar o downgrade no control plane.</li>
 *   <li>Montar a resposta consolidada do tenant.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPlanDowngradeService {

    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final AccountPlanChangeService accountPlanChangeService;

    /**
     * Aplica downgrade elegível via bridge explícita para o public schema.
     *
     * @param command comando consolidado
     * @param preview preview calculado
     * @return resposta consolidada
     */
    public TenantPlanChangeResponse handleDowngrade(
            ChangeAccountPlanCommand command,
            PlanEligibilityResult preview
    ) {
        if (!preview.eligible()) {
            log.warn(
                    "Downgrade rejeitado no tenant por inelegibilidade. accountId={}, currentPlan={}, targetPlan={}, changeType={}",
                    command.accountId(),
                    preview.currentPlan(),
                    preview.targetPlan(),
                    preview.changeType()
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Downgrade não elegível", 409);
        }

        AccountPlanChangeResult result = tenantToPublicBridgeExecutor.call(() ->
                accountPlanChangeService.applyEligibleDowngrade(command)
        );

        log.info(
                "Downgrade aplicado com sucesso no tenant. accountId={}, oldPlan={}, newPlan={}, changeType={}",
                result.accountId(),
                result.oldPlan(),
                result.newPlan(),
                result.changeType()
        );

        return new TenantPlanChangeResponse(
                result.accountId(),
                result.oldPlan().name(),
                result.newPlan().name(),
                result.newPlan().name(),
                result.changeType().name(),
                true,
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