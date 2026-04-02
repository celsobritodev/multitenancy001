package brito.com.multitenancy001.controlplane.billing.app;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.billing.app.audit.ControlPlaneBillingSecurityAuditRecorder;
import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.controlplane.billing.persistence.ControlPlanePaymentRepository;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação responsável pelo fluxo principal de pagamentos do Control Plane.
 *
 * <p>Objetivos desta versão:</p>
 * <ul>
 *   <li>Garantir fluxo determinístico de criação, processamento e conclusão de pagamento.</li>
 *   <li>Usar idempotência por chave persistida como mecanismo principal.</li>
 *   <li>Tratar colisão real de idempotência no banco com retry-safe.</li>
 *   <li>Enfileirar upgrades aprovados sem duplicação.</li>
 *   <li>Mapear corretamente o domínio {@link Payment} para o DTO {@link PaymentResponse} real do projeto.</li>
 * </ul>
 *
 * <p>Regras importantes:</p>
 * <ul>
 *   <li>Upgrade de plano exige {@code idempotencyKey} obrigatória.</li>
 *   <li>O service opera em contexto PUBLIC via {@link PublicSchemaUnitOfWork}.</li>
 *   <li>Conclusão de pagamento usa lock pessimista para endurecimento de concorrência.</li>
 *   <li>Falha de pagamento usa invariantes do domínio via {@link Payment#markAsFailed(String)}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentService {

    private final PublicSchemaUnitOfWork uow;
    private final AccountRepository accountRepository;
    private final ControlPlanePaymentRepository paymentRepository;
    private final ControlPlaneRequestIdentityService identity;
    private final AppClock clock;
    private final ControlPlaneBillingSecurityAuditRecorder audit;

    /**
     * Fila em memória para pagamentos que exigem binding de upgrade.
     */
    private final ConcurrentLinkedQueue<Long> upgradeQueue = new ConcurrentLinkedQueue<>();

    /**
     * Conjunto auxiliar para evitar enfileiramento duplicado do mesmo pagamento.
     */
    private final Set<Long> upgradeSet = ConcurrentHashMap.newKeySet();

    /**
     * Processa um pagamento administrativo para uma conta explícita.
     *
     * @param req request administrativo validado
     * @return resposta consolidada do pagamento
     */
    public PaymentResponse processPaymentForAccount(AdminPaymentRequest req) {
        log.info("========== processPaymentForAccount INICIADO ==========");

        validateAdminRequest(req);

        String idempotencyKey = normalize(req.idempotencyKey());

        Map<String, Object> details = audit.baseDetails("payment_create_admin", req.accountId(), null);
        details.put("purpose", req.purpose());
        details.put("targetPlan", req.targetPlan());
        details.put("amount", req.amount());
        details.put("idempotencyKey", idempotencyKey);

        audit.recordAttempt(SecurityAuditActionType.PAYMENT_CREATED, req.accountId(), null, details);

        Payment existing = findByIdempotency(idempotencyKey);
        if (existing != null) {
            log.warn("Pagamento administrativo idempotente reutilizado. paymentId={}, accountId={}",
                    existing.getId(),
                    existing.getAccount() != null ? existing.getAccount().getId() : null);

            details.put("paymentId", existing.getId());
            details.put("status", existing.getStatus() != null ? existing.getStatus().name() : null);

            audit.recordSuccess(
                    SecurityAuditActionType.PAYMENT_CREATED,
                    existing.getAccount() != null ? existing.getAccount().getId() : req.accountId(),
                    existing.getAccount() != null ? existing.getAccount().getLoginEmail() : null,
                    details
            );

            return map(existing);
        }

        Instant now = clock.instant();

        try {
            Payment payment = createPaymentAdmin(req, now, idempotencyKey);

            details.put("paymentId", payment.getId());
            details.put("status", payment.getStatus() != null ? payment.getStatus().name() : null);

            boolean approved = processGateway(payment.getId(), req);

            if (approved) {
                Payment completed = finalizePayment(payment.getId(), now);
                handleUpgradeIfNeeded(completed);

                audit.recordSuccess(
                        SecurityAuditActionType.PAYMENT_CREATED,
                        completed.getAccount() != null ? completed.getAccount().getId() : req.accountId(),
                        completed.getAccount() != null ? completed.getAccount().getLoginEmail() : null,
                        details
                );

                log.info("Pagamento administrativo concluído com sucesso. paymentId={}, status={}",
                        completed.getId(),
                        completed.getStatus());

                return map(completed);
            }

            failPayment(payment.getId());

            details.put("status", PaymentStatus.FAILED.name());
            audit.recordFailure(
                    SecurityAuditActionType.PAYMENT_STATUS_CHANGED,
                    req.accountId(),
                    payment.getAccount() != null ? payment.getAccount().getLoginEmail() : null,
                    details
            );

            log.warn("Pagamento administrativo recusado. paymentId={}", payment.getId());
            throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Pagamento recusado", 402);

        } catch (ApiException ex) {
            details.put("exception", ex.getClass().getSimpleName());
            audit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, req.accountId(), null, details);
            log.error("Erro de negócio ao processar pagamento administrativo. accountId={}, motivo={}",
                    req.accountId(),
                    ex.getMessage(),
                    ex);
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            audit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, req.accountId(), null, details);
            log.error("Erro inesperado ao processar pagamento administrativo. accountId={}",
                    req.accountId(),
                    ex);
            throw ex;
        }
    }

    /**
     * Processa um pagamento self-service para a conta autenticada.
     *
     * @param req request self-service
     * @return resposta consolidada do pagamento
     */
    public PaymentResponse processPaymentForMyAccount(PaymentRequest req) {
        log.info("========== processPaymentForMyAccount INICIADO ==========");

        if (req == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido", 400);
        }

        Long accountId = identity.getCurrentAccountId();
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Não autenticado", 401);
        }

        String idempotencyKey = normalize(req.idempotencyKey());

        Map<String, Object> details = audit.baseDetails("payment_create_self", accountId, null);
        details.put("purpose", req.purpose());
        details.put("targetPlan", req.targetPlan());
        details.put("amount", req.amount());
        details.put("idempotencyKey", idempotencyKey);

        audit.recordAttempt(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, details);

        Payment existing = findByIdempotency(idempotencyKey);
        if (existing != null) {
            log.warn("Pagamento self-service idempotente reutilizado. paymentId={}, accountId={}",
                    existing.getId(),
                    existing.getAccount() != null ? existing.getAccount().getId() : accountId);

            details.put("paymentId", existing.getId());
            details.put("status", existing.getStatus() != null ? existing.getStatus().name() : null);

            audit.recordSuccess(
                    SecurityAuditActionType.PAYMENT_CREATED,
                    existing.getAccount() != null ? existing.getAccount().getId() : accountId,
                    existing.getAccount() != null ? existing.getAccount().getLoginEmail() : null,
                    details
            );

            return map(existing);
        }

        Instant now = clock.instant();

        try {
            Payment payment = createPaymentSelf(accountId, req, now, idempotencyKey);

            details.put("paymentId", payment.getId());
            details.put("status", payment.getStatus() != null ? payment.getStatus().name() : null);

            boolean approved = processGateway(payment.getId(), req);

            if (approved) {
                Payment completed = finalizePayment(payment.getId(), now);
                handleUpgradeIfNeeded(completed);

                audit.recordSuccess(
                        SecurityAuditActionType.PAYMENT_CREATED,
                        completed.getAccount() != null ? completed.getAccount().getId() : accountId,
                        completed.getAccount() != null ? completed.getAccount().getLoginEmail() : null,
                        details
                );

                log.info("Pagamento self-service concluído com sucesso. paymentId={}, status={}",
                        completed.getId(),
                        completed.getStatus());

                return map(completed);
            }

            failPayment(payment.getId());

            details.put("status", PaymentStatus.FAILED.name());
            audit.recordFailure(
                    SecurityAuditActionType.PAYMENT_STATUS_CHANGED,
                    accountId,
                    payment.getAccount() != null ? payment.getAccount().getLoginEmail() : null,
                    details
            );

            log.warn("Pagamento self-service recusado. paymentId={}", payment.getId());
            throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Pagamento recusado", 402);

        } catch (ApiException ex) {
            details.put("exception", ex.getClass().getSimpleName());
            audit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, details);
            log.error("Erro de negócio ao processar pagamento self-service. accountId={}, motivo={}",
                    accountId,
                    ex.getMessage(),
                    ex);
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            audit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, details);
            log.error("Erro inesperado ao processar pagamento self-service. accountId={}",
                    accountId,
                    ex);
            throw ex;
        }
    }

    /**
     * Cria pagamento administrativo em transação PUBLIC.
     *
     * <p>Se houver colisão de {@code idempotencyKey}, reaproveita o pagamento já persistido.</p>
     *
     * @param req request administrativo
     * @param now instante atual
     * @param key chave de idempotência normalizada
     * @return pagamento persistido
     */
    private Payment createPaymentAdmin(AdminPaymentRequest req, Instant now, String key) {
        return uow.tx(() -> {
            Account acc = accountRepository.findById(req.accountId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

            Payment p = Payment.builder()
                    .account(acc)
                    .amount(req.amount())
                    .paymentMethod(req.paymentMethod())
                    .paymentGateway(req.paymentGateway())
                    .description(normalize(req.description()))
                    .status(PaymentStatus.PENDING)
                    .paymentDate(now)
                    .targetPlan(req.targetPlan())
                    .billingCycle(req.billingCycle())
                    .paymentPurpose(req.purpose())
                    .planPriceSnapshot(req.planPriceSnapshot())
                    .currency(req.currencyCode())
                    .effectiveFrom(req.effectiveFrom())
                    .coverageEndDate(req.coverageEndDate())
                    .validUntil(req.coverageEndDate())
                    .idempotencyKey(key)
                    .build();

            try {
                Payment saved = paymentRepository.save(p);

                log.info("Pagamento administrativo criado. paymentId={}, accountId={}, idempotencyKey={}",
                        saved.getId(),
                        acc.getId(),
                        saved.getIdempotencyKey());

                return saved;
            } catch (DataIntegrityViolationException ex) {
                if (!StringUtils.hasText(key)) {
                    throw ex;
                }

                log.warn("Colisão de idempotência detectada na criação ADMIN. accountId={}, idempotencyKey={}",
                        acc.getId(),
                        key);

                return paymentRepository.findByIdempotencyKeyWithAccount(key)
                        .orElseThrow(() -> ex);
            }
        });
    }

    /**
     * Cria pagamento self-service em transação PUBLIC.
     *
     * <p>Se houver colisão de {@code idempotencyKey}, reaproveita o pagamento já persistido.</p>
     *
     * @param accountId conta autenticada
     * @param req request self-service
     * @param now instante atual
     * @param key chave de idempotência normalizada
     * @return pagamento persistido
     */
    private Payment createPaymentSelf(Long accountId, PaymentRequest req, Instant now, String key) {
        return uow.tx(() -> {
            Account acc = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

            Payment p = Payment.builder()
                    .account(acc)
                    .amount(req.amount())
                    .paymentMethod(req.paymentMethod())
                    .paymentGateway(req.paymentGateway())
                    .description(normalize(req.description()))
                    .status(PaymentStatus.PENDING)
                    .paymentDate(now)
                    .targetPlan(req.targetPlan())
                    .billingCycle(req.billingCycle())
                    .paymentPurpose(req.purpose())
                    .planPriceSnapshot(req.planPriceSnapshot())
                    .currency(req.currencyCode())
                    .effectiveFrom(req.effectiveFrom())
                    .coverageEndDate(req.coverageEndDate())
                    .validUntil(req.coverageEndDate())
                    .idempotencyKey(key)
                    .build();

            try {
                Payment saved = paymentRepository.save(p);

                log.info("Pagamento self-service criado. paymentId={}, accountId={}, idempotencyKey={}",
                        saved.getId(),
                        acc.getId(),
                        saved.getIdempotencyKey());

                return saved;
            } catch (DataIntegrityViolationException ex) {
                if (!StringUtils.hasText(key)) {
                    throw ex;
                }

                log.warn("Colisão de idempotência detectada na criação SELF. accountId={}, idempotencyKey={}",
                        acc.getId(),
                        key);

                return paymentRepository.findByIdempotencyKeyWithAccount(key)
                        .orElseThrow(() -> ex);
            }
        });
    }

    /**
     * Simula o processamento de gateway.
     *
     * <p>Substituir futuramente por integração real.</p>
     *
     * @param paymentId id do pagamento
     * @param req request original
     * @return true quando aprovado
     */
    private boolean processGateway(Long paymentId, Object req) {
        log.info("Chamando gateway de pagamento. paymentId={}, requestType={}",
                paymentId,
                req != null ? req.getClass().getSimpleName() : null);
        return true;
    }

    /**
     * Finaliza pagamento em transação isolada com lock pessimista.
     *
     * @param paymentId id do pagamento
     * @param now instante atual
     * @return pagamento finalizado
     */
    private Payment finalizePayment(Long paymentId, Instant now) {
        return uow.requiresNew(() -> {
            Payment p = paymentRepository.findByIdWithAccountForUpdate(paymentId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

            if (p.isCompleted()) {
                log.info("Pagamento já estava completado. paymentId={}", paymentId);
                return p;
            }

            p.markAsCompleted(now);
            Payment saved = paymentRepository.save(p);

            log.info("Pagamento finalizado com sucesso. paymentId={}, status={}",
                    saved.getId(),
                    saved.getStatus());

            return saved;
        });
    }

    /**
     * Marca pagamento como falho usando invariante do domínio.
     *
     * @param paymentId id do pagamento
     */
    private void failPayment(Long paymentId) {
        uow.tx(() -> {
            Payment p = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

            p.markAsFailed("Falha no processamento do pagamento");
            paymentRepository.save(p);

            log.warn("Pagamento marcado como FAILED. paymentId={}", paymentId);
            return null;
        });
    }

    /**
     * Enfileira upgrade quando o pagamento exigir binding de plano.
     *
     * <p>Somente pagamentos COMPLETED com {@code targetPlan} válido entram na fila.</p>
     *
     * @param p pagamento finalizado
     */
    private void handleUpgradeIfNeeded(Payment p) {
        if (!p.requiresPlanBinding()) {
            return;
        }

        if (!p.isCompleted()) {
            log.warn("Pagamento com binding de plano ainda não está COMPLETED. paymentId={}, status={}",
                    p.getId(),
                    p.getStatus());
            return;
        }

        if (p.getTargetPlan() == null) {
            log.warn("Pagamento de upgrade sem targetPlan. paymentId={}", p.getId());
            return;
        }

        if (upgradeSet.add(p.getId())) {
            upgradeQueue.add(p.getId());
            log.info("Pagamento de upgrade enfileirado. paymentId={}", p.getId());
        } else {
            log.info("Pagamento de upgrade já estava enfileirado. paymentId={}", p.getId());
        }
    }

    /**
     * Busca pagamento por chave de idempotência.
     *
     * @param key chave normalizada
     * @return pagamento encontrado ou null
     */
    private Payment findByIdempotency(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }

        return uow.readOnly(() -> paymentRepository.findByIdempotencyKeyWithAccount(key).orElse(null));
    }

    /**
     * Valida request administrativo.
     *
     * @param req request a validar
     */
    private void validateAdminRequest(AdminPaymentRequest req) {
        if (req == null || req.accountId() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "accountId obrigatório", 400);
        }

        if (req.purpose() == PaymentPurpose.PLAN_UPGRADE
                && !StringUtils.hasText(req.idempotencyKey())) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "idempotencyKey obrigatório para upgrade",
                    400
            );
        }
    }

    /**
     * Mapeia entidade de domínio para response DTO completo.
     *
     * <p>Observação:</p>
     * <ul>
     *   <li>O domínio {@link Payment} não possui campo {@code paidAt} separado.</li>
     *   <li>No estado atual do projeto, o instante mais adequado para preencher
     *       esse campo no DTO é {@code paymentDate}.</li>
     * </ul>
     *
     * @param p pagamento de domínio
     * @return response completo
     */
    private PaymentResponse map(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getAccount() != null ? p.getAccount().getId() : null,

                p.getAmount(),
                p.getPaymentMethod(),
                p.getPaymentGateway(),
                p.getStatus(),

                p.getDescription(),

                p.getTargetPlan(),
                p.getBillingCycle(),
                p.getPaymentPurpose(),
                p.getPlanPriceSnapshot(),
                p.getCurrency(),
                p.getEffectiveFrom(),
                p.getCoverageEndDate(),

                p.getPaymentDate(),
                p.getValidUntil(),
                p.getRefundedAt(),

                p.getAudit() != null ? p.getAudit().getCreatedAt() : null,
                p.getAudit() != null ? p.getAudit().getUpdatedAt() : null
        );
    }

    /**
     * Normaliza string opcional.
     *
     * @param v valor bruto
     * @return valor normalizado ou null
     */
    private String normalize(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}