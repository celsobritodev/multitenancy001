package brito.com.multitenancy001.controlplane.billing.app;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.billing.app.audit.ControlPlaneBillingSecurityAuditRecorder;
import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de comando responsável pelo fluxo principal de pagamentos do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Receber requests de pagamento administrativo e self-service.</li>
 *   <li>Validar entrada semântica e identidade do chamador.</li>
 *   <li>Aplicar idempotência forte por chave persistida.</li>
 *   <li>Criar pagamento PENDING, processar gateway, concluir/falhar e enfileirar upgrade.</li>
 *   <li>Registrar auditoria de tentativa, sucesso e falha.</li>
 * </ul>
 *
 * <p>Este serviço coordena o caso de uso, mas delega responsabilidades específicas para:</p>
 * <ul>
 *   <li>{@link ControlPlanePaymentLifecycleService}</li>
 *   <li>{@link ControlPlanePaymentRequestValidator}</li>
 *   <li>{@link ControlPlanePaymentUpgradeEnqueueService}</li>
 *   <li>{@link ControlPlanePaymentApiResponseMapper}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentCommandService {

    private final ControlPlanePaymentLifecycleService controlPlanePaymentLifecycleService;
    private final ControlPlanePaymentRequestValidator controlPlanePaymentRequestValidator;
    private final ControlPlanePaymentUpgradeEnqueueService controlPlanePaymentUpgradeEnqueueService;
    private final ControlPlanePaymentApiResponseMapper controlPlanePaymentResponseMapper;
    private final ControlPlaneRequestIdentityService controlPlaneRequestIdentityService;
    private final ControlPlaneBillingSecurityAuditRecorder controlPlaneBillingSecurityAuditRecorder;
    private final AppClock appClock;

    /**
     * Processa um pagamento administrativo para uma conta explícita.
     *
     * @param adminPaymentRequest request administrativo
     * @return resposta consolidada do pagamento
     */
    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {
        log.info("========== processPaymentForAccount INICIADO ==========");

        controlPlanePaymentRequestValidator.validateAdminRequest(adminPaymentRequest);

        final Long accountId = adminPaymentRequest.accountId();
        final String idempotencyKey = controlPlanePaymentRequestValidator.normalize(adminPaymentRequest.idempotencyKey());

        Map<String, Object> details =
                controlPlaneBillingSecurityAuditRecorder.baseDetails("payment_create_admin", accountId, null);
        details.put("purpose", adminPaymentRequest.purpose());
        details.put("targetPlan", adminPaymentRequest.targetPlan());
        details.put("amount", adminPaymentRequest.amount());
        details.put("idempotencyKey", idempotencyKey);

        controlPlaneBillingSecurityAuditRecorder.recordAttempt(
                SecurityAuditActionType.PAYMENT_CREATED,
                accountId,
                null,
                details
        );

        try {
            Payment existing = controlPlanePaymentLifecycleService.findByIdempotency(idempotencyKey);
            if (existing != null) {
                log.warn("Pagamento administrativo idempotente reutilizado. paymentId={}, accountId={}",
                        existing.getId(),
                        existing.getAccount() != null ? existing.getAccount().getId() : null);

                details.put("paymentId", existing.getId());
                details.put("status", existing.getStatus() != null ? existing.getStatus().name() : null);

                controlPlaneBillingSecurityAuditRecorder.recordSuccess(
                        SecurityAuditActionType.PAYMENT_CREATED,
                        existing.getAccount() != null ? existing.getAccount().getId() : accountId,
                        existing.getAccount() != null ? existing.getAccount().getLoginEmail() : null,
                        details
                );

                return controlPlanePaymentResponseMapper.toResponse(existing);
            }

            Instant now = appClock.instant();

            Payment payment = controlPlanePaymentLifecycleService.createPaymentAdmin(
                    adminPaymentRequest,
                    now,
                    idempotencyKey
            );

            details.put("paymentId", payment.getId());
            details.put("status", payment.getStatus() != null ? payment.getStatus().name() : null);

            boolean approved = processGateway(payment.getId(), adminPaymentRequest);
            if (approved) {
                Payment completed = controlPlanePaymentLifecycleService.finalizePayment(payment.getId(), now);
                controlPlanePaymentUpgradeEnqueueService.enqueueIfRequired(completed);

                details.put("status", completed.getStatus() != null ? completed.getStatus().name() : null);

                controlPlaneBillingSecurityAuditRecorder.recordSuccess(
                        SecurityAuditActionType.PAYMENT_CREATED,
                        completed.getAccount() != null ? completed.getAccount().getId() : accountId,
                        completed.getAccount() != null ? completed.getAccount().getLoginEmail() : null,
                        details
                );

                log.info("Pagamento administrativo concluído com sucesso. paymentId={}, status={}",
                        completed.getId(),
                        completed.getStatus());

                return controlPlanePaymentResponseMapper.toResponse(completed);
            }

            controlPlanePaymentLifecycleService.failPayment(payment.getId(), "Falha no processamento do pagamento");

            details.put("status", PaymentStatus.FAILED.name());

            controlPlaneBillingSecurityAuditRecorder.recordFailure(
                    SecurityAuditActionType.PAYMENT_STATUS_CHANGED,
                    accountId,
                    payment.getAccount() != null ? payment.getAccount().getLoginEmail() : null,
                    details
            );

            log.warn("Pagamento administrativo recusado. paymentId={}", payment.getId());
            throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Pagamento recusado", 402);

        } catch (ApiException ex) {
            details.put("exception", ex.getClass().getSimpleName());

            controlPlaneBillingSecurityAuditRecorder.recordFailure(
                    SecurityAuditActionType.PAYMENT_CREATED,
                    accountId,
                    null,
                    details
            );

            log.error("Erro de negócio ao processar pagamento administrativo. accountId={}, motivo={}",
                    accountId,
                    ex.getMessage(),
                    ex);
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());

            controlPlaneBillingSecurityAuditRecorder.recordFailure(
                    SecurityAuditActionType.PAYMENT_CREATED,
                    accountId,
                    null,
                    details
            );

            log.error("Erro inesperado ao processar pagamento administrativo. accountId={}",
                    accountId,
                    ex);
            throw ex;
        }
    }

    /**
     * Processa um pagamento self-service para a conta autenticada.
     *
     * @param paymentRequest request self-service
     * @return resposta consolidada do pagamento
     */
    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        log.info("========== processPaymentForMyAccount INICIADO ==========");

        controlPlanePaymentRequestValidator.validateSelfRequest(paymentRequest);

        Long accountId = controlPlaneRequestIdentityService.getCurrentAccountId();
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Não autenticado", 401);
        }

        final String idempotencyKey = controlPlanePaymentRequestValidator.normalize(paymentRequest.idempotencyKey());

        Map<String, Object> details =
                controlPlaneBillingSecurityAuditRecorder.baseDetails("payment_create_self", accountId, null);
        details.put("purpose", paymentRequest.purpose());
        details.put("targetPlan", paymentRequest.targetPlan());
        details.put("amount", paymentRequest.amount());
        details.put("idempotencyKey", idempotencyKey);

        controlPlaneBillingSecurityAuditRecorder.recordAttempt(
                SecurityAuditActionType.PAYMENT_CREATED,
                accountId,
                null,
                details
        );

        try {
            Payment existing = controlPlanePaymentLifecycleService.findByIdempotency(idempotencyKey);
            if (existing != null) {
                log.warn("Pagamento self-service idempotente reutilizado. paymentId={}, accountId={}",
                        existing.getId(),
                        existing.getAccount() != null ? existing.getAccount().getId() : accountId);

                details.put("paymentId", existing.getId());
                details.put("status", existing.getStatus() != null ? existing.getStatus().name() : null);

                controlPlaneBillingSecurityAuditRecorder.recordSuccess(
                        SecurityAuditActionType.PAYMENT_CREATED,
                        existing.getAccount() != null ? existing.getAccount().getId() : accountId,
                        existing.getAccount() != null ? existing.getAccount().getLoginEmail() : null,
                        details
                );

                return controlPlanePaymentResponseMapper.toResponse(existing);
            }

            Instant now = appClock.instant();

            Payment payment = controlPlanePaymentLifecycleService.createPaymentSelf(
                    accountId,
                    paymentRequest,
                    now,
                    idempotencyKey
            );

            details.put("paymentId", payment.getId());
            details.put("status", payment.getStatus() != null ? payment.getStatus().name() : null);

            boolean approved = processGateway(payment.getId(), paymentRequest);
            if (approved) {
                Payment completed = controlPlanePaymentLifecycleService.finalizePayment(payment.getId(), now);
                controlPlanePaymentUpgradeEnqueueService.enqueueIfRequired(completed);

                details.put("status", completed.getStatus() != null ? completed.getStatus().name() : null);

                controlPlaneBillingSecurityAuditRecorder.recordSuccess(
                        SecurityAuditActionType.PAYMENT_CREATED,
                        completed.getAccount() != null ? completed.getAccount().getId() : accountId,
                        completed.getAccount() != null ? completed.getAccount().getLoginEmail() : null,
                        details
                );

                log.info("Pagamento self-service concluído com sucesso. paymentId={}, status={}",
                        completed.getId(),
                        completed.getStatus());

                return controlPlanePaymentResponseMapper.toResponse(completed);
            }

            controlPlanePaymentLifecycleService.failPayment(payment.getId(), "Falha no processamento do pagamento");

            details.put("status", PaymentStatus.FAILED.name());

            controlPlaneBillingSecurityAuditRecorder.recordFailure(
                    SecurityAuditActionType.PAYMENT_STATUS_CHANGED,
                    accountId,
                    payment.getAccount() != null ? payment.getAccount().getLoginEmail() : null,
                    details
            );

            log.warn("Pagamento self-service recusado. paymentId={}", payment.getId());
            throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Pagamento recusado", 402);

        } catch (ApiException ex) {
            details.put("exception", ex.getClass().getSimpleName());

            controlPlaneBillingSecurityAuditRecorder.recordFailure(
                    SecurityAuditActionType.PAYMENT_CREATED,
                    accountId,
                    null,
                    details
            );

            log.error("Erro de negócio ao processar pagamento self-service. accountId={}, motivo={}",
                    accountId,
                    ex.getMessage(),
                    ex);
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());

            controlPlaneBillingSecurityAuditRecorder.recordFailure(
                    SecurityAuditActionType.PAYMENT_CREATED,
                    accountId,
                    null,
                    details
            );

            log.error("Erro inesperado ao processar pagamento self-service. accountId={}",
                    accountId,
                    ex);
            throw ex;
        }
    }

    /**
     * Simula o processamento do gateway de pagamento.
     *
     * <p>Este método é um placeholder para futura integração real.
     * Quando a integração concreta for implantada, recomenda-se extrair
     * essa responsabilidade para um {@code IntegrationService} dedicado.</p>
     *
     * @param paymentId id do pagamento
     * @param request request original
     * @return {@code true} quando aprovado
     */
    private boolean processGateway(Long paymentId, Object request) {
        log.info("Chamando gateway de pagamento. paymentId={}, requestType={}",
                paymentId,
                request != null ? request.getClass().getSimpleName() : null);
        return true;
    }
}