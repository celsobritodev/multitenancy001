package brito.com.multitenancy001.controlplane.accounts.api.subscription;

import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangePreviewRequest;
import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangePreviewResponse;
import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangeRequest;
import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountSubscriptionAdminResponse;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.ControlPlaneAccountSubscriptionCommandService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.ControlPlaneAccountSubscriptionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller ADMIN de assinatura/plano para contas no Control Plane.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Controller usa apenas DTOs.</li>
 *   <li>Controller não acessa repository.</li>
 *   <li>Controller delega integralmente para query/command services.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/controlplane/accounts")
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountSubscriptionAdminController {

    private final ControlPlaneAccountSubscriptionQueryService queryService;
    private final ControlPlaneAccountSubscriptionCommandService commandService;

    /**
     * Consulta a assinatura consolidada da conta.
     *
     * @param accountId id da conta
     * @return resposta consolidada
     */
    @GetMapping("/{accountId}/subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountSubscriptionAdminResponse> getSubscription(
            @PathVariable Long accountId
    ) {
        log.info("HTTP GET /api/controlplane/accounts/{}/subscription", accountId);
        return ResponseEntity.ok(queryService.getSubscription(accountId));
    }

    /**
     * Alias explícito para consulta de limites.
     *
     * @param accountId id da conta
     * @return resposta consolidada
     */
    @GetMapping("/{accountId}/subscription/limits")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountSubscriptionAdminResponse> getLimits(
            @PathVariable Long accountId
    ) {
        log.info("HTTP GET /api/controlplane/accounts/{}/subscription/limits", accountId);
        return ResponseEntity.ok(queryService.getSubscription(accountId));
    }

    /**
     * Executa preview de mudança de plano.
     *
     * @param accountId id da conta
     * @param request request validado
     * @return preview
     */
    @PostMapping("/{accountId}/subscription/preview-change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountPlanChangePreviewResponse> previewChange(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountPlanChangePreviewRequest request
    ) {
        log.info(
                "HTTP POST /api/controlplane/accounts/{}/subscription/preview-change. targetPlan={}",
                accountId,
                request.targetPlan()
        );
        return ResponseEntity.ok(queryService.previewChange(accountId, request.targetPlan()));
    }

    /**
     * Solicita mudança efetiva de plano.
     *
     * @param accountId id da conta
     * @param request request validado
     * @return resultado final
     */
    @PostMapping("/{accountId}/subscription/change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountPlanChangeResponse> changePlan(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountPlanChangeRequest request
    ) {
        log.info(
                "HTTP POST /api/controlplane/accounts/{}/subscription/change. targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}, amount={}",
                accountId,
                request.targetPlan(),
                request.billingCycle(),
                request.paymentMethod(),
                request.paymentGateway(),
                request.amount()
        );

        return ResponseEntity.ok(
                commandService.changePlan(
                        accountId,
                        request.targetPlan(),
                        request.billingCycle(),
                        request.paymentMethod(),
                        request.paymentGateway(),
                        request.amount(),
                        request.planPriceSnapshot(),
                        request.currencyCode(),
                        "controlplane_authenticated_admin",
                        request.reason()
                )
        );
    }
}