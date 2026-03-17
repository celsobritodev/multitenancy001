package brito.com.multitenancy001.tenant.subscription.api;

import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangePreviewRequest;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangePreviewResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangeRequest;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangeResponse;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanLimitsResponse;
import brito.com.multitenancy001.tenant.subscription.app.TenantSubscriptionCommandService;
import brito.com.multitenancy001.tenant.subscription.app.TenantSubscriptionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de assinatura/plano no contexto do Tenant.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Controller usa apenas DTOs.</li>
 *   <li>Controller não acessa repository.</li>
 *   <li>Controller delega integralmente para query/command services.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/subscription")
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionController {

    private final TenantSubscriptionQueryService queryService;
    private final TenantSubscriptionCommandService commandService;

    /**
     * Consulta limites/uso da conta autenticada.
     *
     * @return visão consolidada
     */
    @GetMapping("/me/limits")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TenantPlanLimitsResponse> getMyLimits() {
        log.info("HTTP GET /api/tenant/subscription/me/limits");
        return ResponseEntity.ok(queryService.getMyLimits());
    }

    /**
     * Executa preview de mudança de plano da conta autenticada.
     *
     * @param request request validado
     * @return preview
     */
    @PostMapping("/me/change-plan-preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TenantPlanChangePreviewResponse> previewChange(
            @Valid @RequestBody TenantPlanChangePreviewRequest request
    ) {
        log.info(
                "HTTP POST /api/tenant/subscription/me/change-plan-preview. targetPlan={}",
                request.targetPlan()
        );
        return ResponseEntity.ok(queryService.previewChange(request.targetPlan()));
    }

    /**
     * Solicita mudança efetiva de plano da conta autenticada.
     *
     * @param request request validado
     * @return resultado final
     */
    @PostMapping("/me/change-plan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TenantPlanChangeResponse> changePlan(
            @Valid @RequestBody TenantPlanChangeRequest request
    ) {
        log.info(
                "HTTP POST /api/tenant/subscription/me/change-plan. targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}, amount={}",
                request.targetPlan(),
                request.billingCycle(),
                request.paymentMethod(),
                request.paymentGateway(),
                request.amount()
        );

        return ResponseEntity.ok(
                commandService.changePlan(
                        request.targetPlan(),
                        request.billingCycle(),
                        request.paymentMethod(),
                        request.paymentGateway(),
                        request.amount(),
                        request.planPriceSnapshot(),
                        request.currencyCode(),
                        request.reason()
                )
        );
    }
}