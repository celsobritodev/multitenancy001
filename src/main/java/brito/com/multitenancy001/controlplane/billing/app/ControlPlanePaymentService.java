package brito.com.multitenancy001.controlplane.billing.app;

import brito.com.multitenancy001.controlplane.accounts.app.AccountStatusService;
import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.ChangeAccountPlanCommand;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangeType;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
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
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application Service do Control Plane para Billing/Payments (public schema).
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Controller não grava auditoria; auditoria é responsabilidade do AppService.</li>
 *   <li>Auditoria:
 *     <ul>
 *       <li>create payment -> PAYMENT_CREATED (ATTEMPT/SUCCESS/DENIED/FAILURE)</li>
 *       <li>status change -> PAYMENT_STATUS_CHANGED (ATTEMPT/SUCCESS/DENIED/FAILURE)</li>
 *     </ul>
 *   </li>
 *   <li>Quando o pagamento for de {@link PaymentPurpose#PLAN_UPGRADE}, a conclusão do pagamento
 *       dispara a aplicação do upgrade via {@link AccountPlanChangeService}.</li>
 *   <li>Fonte única de tempo: {@link AppClock}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    private final AccountRepository accountRepository;
    private final ControlPlanePaymentRepository controlPlanePaymentRepository;
    private final ControlPlaneRequestIdentityService requestIdentity;
    private final AppClock appClock;
    private final AccountStatusService accountStatusService;
    private final AccountPlanChangeService accountPlanChangeService;

    private final ControlPlaneBillingSecurityAuditRecorder billingAudit;

    // =========================================================
    // Scheduled
    // =========================================================

    /**
     * Job de verificação de pagamentos, trials expirados, contas overdue e pendências antigas.
     */
    @Scheduled(cron = "${app.payment.check-cron:0 0 0 * * *}")
    public void checkPayments() {
        log.info("Iniciando verificação de pagamentos...");
        Instant now = appClock.instant();

        List<Long> expiredTrialIds = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findExpiredTrialIdsNotDeleted(now, AccountStatus.FREE_TRIAL)
        );

        for (Long accountId : expiredTrialIds) {
            suspendAccountById(accountId, "Trial expirado");
        }

        LocalDate todayUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);

        List<Long> overdueAccountIds = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findOverdueAccountIdsNotDeleted(AccountStatus.ACTIVE, todayUtc)
        );

        for (Long accountId : overdueAccountIds) {
            suspendAccountById(accountId, "Pagamento atrasado");
        }

        checkExpiredPendingPayments(now);
    }

    /**
     * Suspende conta por id.
     *
     * @param accountId id da conta
     * @param reason motivo
     */
    private void suspendAccountById(Long accountId, String reason) {
        accountStatusService.changeAccountStatus(
                accountId,
                new AccountStatusChangeCommand(AccountStatus.SUSPENDED, reason, "billing_job")
        );

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findAnyById(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404))
        );

        sendSuspensionEmail(account, reason);
    }

    /**
     * Expira pagamentos pendentes antigos.
     *
     * @param now instante atual
     */
    private void checkExpiredPendingPayments(Instant now) {
        Instant thirtyMinutesAgo = now.minusSeconds(30 * 60);

        publicSchemaUnitOfWork.tx(() -> {
            List<Payment> expiredPayments = controlPlanePaymentRepository
                    .findByStatusAndAudit_CreatedAtBefore(PaymentStatus.PENDING, thirtyMinutesAgo);

            for (Payment payment : expiredPayments) {
                Long accountId = payment.getAccount() != null ? payment.getAccount().getId() : null;
                String accountEmail = payment.getAccount() != null ? payment.getAccount().getLoginEmail() : null;

                Map<String, Object> details = billingAudit.baseDetails("payment_status_expire_pending_job", accountId, accountEmail);
                details.put("paymentId", payment.getId());
                details.put("fromStatus", PaymentStatus.PENDING.name());
                details.put("toStatus", PaymentStatus.EXPIRED.name());
                details.put("reason", "Expired pending > 30 minutes");

                billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);

                try {
                    payment.setStatus(PaymentStatus.EXPIRED);
                    controlPlanePaymentRepository.save(payment);
                    billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);
                } catch (Exception ex) {
                    details.put("exception", ex.getClass().getSimpleName());
                    billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);
                    throw ex;
                }
            }
            return null;
        });
    }

    // =========================================================
    // Commands
    // =========================================================

    /**
     * Cria e processa um pagamento administrativo para uma conta.
     *
     * @param adminPaymentRequest request administrativo
     * @return resposta final
     */
    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {
        if (adminPaymentRequest == null || adminPaymentRequest.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Instant now = appClock.instant();

        Map<String, Object> createDetails = billingAudit.baseDetails("payment_create_admin", adminPaymentRequest.accountId(), null);
        createDetails.put("amount", adminPaymentRequest.amount());
        createDetails.put("paymentMethod", adminPaymentRequest.paymentMethod());
        createDetails.put("paymentGateway", adminPaymentRequest.paymentGateway());
        createDetails.put("description", adminPaymentRequest.description());
        createDetails.put("targetPlan", adminPaymentRequest.targetPlan());
        createDetails.put("billingCycle", adminPaymentRequest.billingCycle());
        createDetails.put("purpose", adminPaymentRequest.purpose());

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), null, createDetails);

        Long paymentId;
        String accountEmail;

        try {
            PaymentCreatedSnapshot snap = publicSchemaUnitOfWork.tx(() -> {
                Account account = accountRepository.findById(adminPaymentRequest.accountId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

                validatePayment(account, adminPaymentRequest.amount(), now);
                validateBillingBinding(
                        account,
                        adminPaymentRequest.targetPlan(),
                        adminPaymentRequest.billingCycle(),
                        adminPaymentRequest.purpose()
                );

                Payment payment = Payment.builder()
                        .account(account)
                        .amount(adminPaymentRequest.amount())
                        .paymentMethod(adminPaymentRequest.paymentMethod())
                        .paymentGateway(adminPaymentRequest.paymentGateway())
                        .description(normalize(adminPaymentRequest.description()))
                        .status(PaymentStatus.PENDING)
                        .paymentDate(now)
                        .targetPlan(adminPaymentRequest.targetPlan())
                        .billingCycle(resolveBillingCycle(adminPaymentRequest.billingCycle()))
                        .paymentPurpose(resolvePurpose(adminPaymentRequest.purpose()))
                        .planPriceSnapshot(resolvePlanPriceSnapshot(adminPaymentRequest.planPriceSnapshot(), adminPaymentRequest.amount()))
                        .currency(resolveCurrencyCode(adminPaymentRequest.currencyCode()))
                        .effectiveFrom(adminPaymentRequest.effectiveFrom())
                        .coverageEndDate(adminPaymentRequest.coverageEndDate())
                        .validUntil(adminPaymentRequest.coverageEndDate())
                        .build();

                Payment saved = controlPlanePaymentRepository.save(payment);
                return new PaymentCreatedSnapshot(saved.getId(), account.getLoginEmail());
            });

            paymentId = snap.paymentId();
            accountEmail = snap.accountEmail();

            createDetails.put("paymentId", paymentId);
            createDetails.put("status", PaymentStatus.PENDING.name());

            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), accountEmail, createDetails);

        } catch (ApiException ex) {
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), null, createDetails, ex);
            throw ex;
        } catch (Exception ex) {
            createDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), null, createDetails);
            throw ex;
        }

        boolean ok = processWithPaymentGateway(
                paymentId,
                new PaymentRequest(
                        adminPaymentRequest.amount(),
                        adminPaymentRequest.paymentMethod(),
                        adminPaymentRequest.paymentGateway(),
                        adminPaymentRequest.description(),
                        adminPaymentRequest.targetPlan(),
                        adminPaymentRequest.billingCycle(),
                        adminPaymentRequest.purpose(),
                        adminPaymentRequest.planPriceSnapshot(),
                        adminPaymentRequest.currencyCode(),
                        adminPaymentRequest.effectiveFrom(),
                        adminPaymentRequest.coverageEndDate()
                )
        );

        if (ok) {
            Map<String, Object> statusDetails = billingAudit.baseDetails("payment_status_change_complete", adminPaymentRequest.accountId(), accountEmail);
            statusDetails.put("paymentId", paymentId);
            statusDetails.put("fromStatus", PaymentStatus.PENDING.name());
            statusDetails.put("toStatus", PaymentStatus.COMPLETED.name());

            billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails);

            try {
                Payment payment = publicSchemaUnitOfWork.tx(() -> completePaymentById(paymentId, now));
                billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails);
                return mapToResponse(payment);
            } catch (ApiException ex) {
                recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails, ex);
                throw ex;
            } catch (Exception ex) {
                statusDetails.put("exception", ex.getClass().getSimpleName());
                billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails);
                throw ex;
            }
        }

        Map<String, Object> failDetails = billingAudit.baseDetails("payment_status_change_fail_gateway", adminPaymentRequest.accountId(), accountEmail);
        failDetails.put("paymentId", paymentId);
        failDetails.put("fromStatus", PaymentStatus.PENDING.name());
        failDetails.put("toStatus", PaymentStatus.FAILED.name());
        failDetails.put("reason", "Falha no processamento do pagamento");

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails);

        try {
            publicSchemaUnitOfWork.tx(() -> {
                failPaymentById(paymentId, "Falha no processamento do pagamento");
                return null;
            });
            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails);
        } catch (ApiException ex) {
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails, ex);
            throw ex;
        } catch (Exception ex) {
            failDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails);
            throw ex;
        }

        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento", 402);
    }

    /**
     * Cria e processa pagamento para a própria conta do usuário autenticado.
     *
     * @param paymentRequest request
     * @return resposta final
     */
    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        if (paymentRequest == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido", 400);
        }

        Long accountId = requestIdentity.getCurrentAccountId();
        Instant now = appClock.instant();

        Map<String, Object> createDetails = billingAudit.baseDetails("payment_create_self", accountId, null);
        createDetails.put("amount", paymentRequest.amount());
        createDetails.put("paymentMethod", paymentRequest.paymentMethod());
        createDetails.put("paymentGateway", paymentRequest.paymentGateway());
        createDetails.put("description", paymentRequest.description());
        createDetails.put("targetPlan", paymentRequest.targetPlan());
        createDetails.put("billingCycle", paymentRequest.billingCycle());
        createDetails.put("purpose", paymentRequest.purpose());

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, createDetails);

        Long paymentId;
        String accountEmail;

        try {
            PaymentCreatedSnapshot snap = publicSchemaUnitOfWork.tx(() -> {
                Account account = accountRepository.findById(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

                validatePayment(account, paymentRequest.amount(), now);
                validateBillingBinding(
                        account,
                        paymentRequest.targetPlan(),
                        paymentRequest.billingCycle(),
                        paymentRequest.purpose()
                );

                Payment payment = Payment.builder()
                        .account(account)
                        .amount(paymentRequest.amount())
                        .paymentMethod(paymentRequest.paymentMethod())
                        .paymentGateway(paymentRequest.paymentGateway())
                        .description(normalize(paymentRequest.description()))
                        .status(PaymentStatus.PENDING)
                        .paymentDate(now)
                        .targetPlan(paymentRequest.targetPlan())
                        .billingCycle(resolveBillingCycle(paymentRequest.billingCycle()))
                        .paymentPurpose(resolvePurpose(paymentRequest.purpose()))
                        .planPriceSnapshot(resolvePlanPriceSnapshot(paymentRequest.planPriceSnapshot(), paymentRequest.amount()))
                        .currency(resolveCurrencyCode(paymentRequest.currencyCode()))
                        .effectiveFrom(paymentRequest.effectiveFrom())
                        .coverageEndDate(paymentRequest.coverageEndDate())
                        .validUntil(paymentRequest.coverageEndDate())
                        .build();

                Payment saved = controlPlanePaymentRepository.save(payment);
                return new PaymentCreatedSnapshot(saved.getId(), account.getLoginEmail());
            });

            paymentId = snap.paymentId();
            accountEmail = snap.accountEmail();

            createDetails.put("paymentId", paymentId);
            createDetails.put("status", PaymentStatus.PENDING.name());

            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_CREATED, accountId, accountEmail, createDetails);

        } catch (ApiException ex) {
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, createDetails, ex);
            throw ex;
        } catch (Exception ex) {
            createDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, createDetails);
            throw ex;
        }

        boolean ok = processWithPaymentGateway(paymentId, paymentRequest);

        if (ok) {
            Map<String, Object> statusDetails = billingAudit.baseDetails("payment_status_change_complete", accountId, accountEmail);
            statusDetails.put("paymentId", paymentId);
            statusDetails.put("fromStatus", PaymentStatus.PENDING.name());
            statusDetails.put("toStatus", PaymentStatus.COMPLETED.name());

            billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails);

            try {
                Payment payment = publicSchemaUnitOfWork.tx(() -> completePaymentById(paymentId, now));
                billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails);
                return mapToResponse(payment);
            } catch (ApiException ex) {
                recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails, ex);
                throw ex;
            } catch (Exception ex) {
                statusDetails.put("exception", ex.getClass().getSimpleName());
                billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails);
                throw ex;
            }
        }

        Map<String, Object> failDetails = billingAudit.baseDetails("payment_status_change_fail_gateway", accountId, accountEmail);
        failDetails.put("paymentId", paymentId);
        failDetails.put("fromStatus", PaymentStatus.PENDING.name());
        failDetails.put("toStatus", PaymentStatus.FAILED.name());
        failDetails.put("reason", "Falha no processamento do pagamento");

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails);

        try {
            publicSchemaUnitOfWork.tx(() -> {
                failPaymentById(paymentId, "Falha no processamento do pagamento");
                return null;
            });
            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails);
        } catch (ApiException ex) {
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails, ex);
            throw ex;
        } catch (Exception ex) {
            failDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails);
            throw ex;
        }

        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento", 402);
    }

    /**
     * Completa manualmente um pagamento pendente.
     *
     * @param paymentId id do pagamento
     * @return response
     */
    public PaymentResponse completePaymentManually(Long paymentId) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);
        }

        Instant now = appClock.instant();

        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );

        Long accountId = payment.getAccount() != null ? payment.getAccount().getId() : null;
        String accountEmail = payment.getAccount() != null ? payment.getAccount().getLoginEmail() : null;

        Map<String, Object> details = billingAudit.baseDetails("payment_complete_manual", accountId, accountEmail);
        details.put("paymentId", paymentId);
        details.put("fromStatus", payment.getStatus() != null ? payment.getStatus().name() : null);
        details.put("toStatus", PaymentStatus.COMPLETED.name());

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);

        try {
            Payment completed = publicSchemaUnitOfWork.tx(() -> {
                Payment p = controlPlanePaymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

                if (p.getStatus() != PaymentStatus.PENDING) {
                    throw new ApiException(ApiErrorCode.INVALID_PAYMENT_STATUS, "Pagamento não está pendente", 409);
                }

                return completePaymentById(paymentId, now);
            });

            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);
            return mapToResponse(completed);

        } catch (ApiException ex) {
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details, ex);
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);
            throw ex;
        }
    }

    /**
     * Reembolsa pagamento.
     *
     * @param paymentId id
     * @param amount valor
     * @param reason motivo
     * @return response
     */
    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);
        }

        Instant now = appClock.instant();

        Payment before = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );

        Long accountId = before.getAccount() != null ? before.getAccount().getId() : null;
        String accountEmail = before.getAccount() != null ? before.getAccount().getLoginEmail() : null;

        Map<String, Object> details = billingAudit.baseDetails("payment_refund", accountId, accountEmail);
        details.put("paymentId", paymentId);
        details.put("amount", amount);
        details.put("reason", reason);
        details.put("fromStatus", before.getStatus() != null ? before.getStatus().name() : null);
        details.put("toStatus", "REFUNDED_OR_PARTIAL");

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);

        try {
            Payment payment = publicSchemaUnitOfWork.tx(() -> {
                Payment p = controlPlanePaymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

                if (!p.canBeRefunded(now)) {
                    throw new ApiException(ApiErrorCode.PAYMENT_NOT_REFUNDABLE, "Pagamento não pode ser reembolsado", 409);
                }

                if (amount == null) {
                    p.refundFully(now, reason);
                } else {
                    p.refundPartially(now, amount, reason);
                }

                return controlPlanePaymentRepository.save(p);
            });

            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);
            return mapToResponse(payment);

        } catch (ApiException ex) {
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details, ex);
            throw ex;
        } catch (Exception ex) {
            details.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, details);
            throw ex;
        }
    }

    // =========================================================
    // Queries
    // =========================================================

    /**
     * Busca pagamento por id para a conta autenticada.
     *
     * @param paymentId id
     * @return response
     */
    public PaymentResponse getPaymentByIdForMyAccount(Long paymentId) {
        Long accountId = requestIdentity.getCurrentAccountId();

        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findScopedByIdAndAccountId(paymentId, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );

        return mapToResponse(payment);
    }

    /**
     * Lista pagamentos da conta autenticada.
     *
     * @return lista
     */
    public List<PaymentResponse> getPaymentsByMyAccount() {
        Long accountId = requestIdentity.getCurrentAccountId();
        return getPaymentsByAccount(accountId);
    }

    /**
     * Verifica se a conta autenticada possui pagamento ativo.
     *
     * @return true se ativo
     */
    public boolean hasActivePaymentMyAccount() {
        Long accountId = requestIdentity.getCurrentAccountId();
        return hasActivePayment(accountId);
    }

    /**
     * Lista pagamentos por conta.
     *
     * @param accountId id da conta
     * @return lista
     */
    public List<PaymentResponse> getPaymentsByAccount(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByAccount_Id(accountId)
                        .stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Verifica se existe pagamento ativo.
     *
     * @param accountId id da conta
     * @return true se existir
     */
    public boolean hasActivePayment(Long accountId) {
        Instant now = appClock.instant();
        return publicSchemaUnitOfWork.readOnly(() -> controlPlanePaymentRepository.existsActivePayment(accountId, now));
    }

    /**
     * Busca pagamento por id.
     *
     * @param paymentId id
     * @return response
     */
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );
        return mapToResponse(payment);
    }

    /**
     * Calcula receita total no período.
     *
     * @param startDate início
     * @param endDate fim
     * @return receita total
     */
    public BigDecimal getTotalRevenue(Instant startDate, Instant endDate) {
        List<Object[]> revenueByAccount = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.getRevenueByAccount(startDate, endDate)
        );

        return revenueByAccount.stream()
                .map(obj -> (BigDecimal) obj[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Verifica se paymentId pertence à conta.
     *
     * @param paymentId id do pagamento
     * @param accountId id da conta
     * @return true se existir
     */
    public boolean paymentExistsForAccount(Long paymentId, Long accountId) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);
        }
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.existsByIdAndAccount_Id(paymentId, accountId)
        );
    }

    /**
     * Lista pagamentos por conta e status.
     *
     * @param accountId id da conta
     * @param status status
     * @return lista
     */
    public List<PaymentResponse> getPaymentsByAccountAndStatus(Long accountId, PaymentStatus status) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        if (status == null) {
            throw new ApiException(ApiErrorCode.INVALID_STATUS, "status é obrigatório", 400);
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByAccount_IdAndStatus(accountId, status)
                        .stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    /**
     * Busca pagamento por transactionId.
     *
     * @param transactionId transactionId
     * @return response
     */
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            throw new ApiException(ApiErrorCode.INVALID_TRANSACTION_ID, "transactionId é obrigatório", 400);
        }

        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByTransactionId(transactionId.trim())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );

        return mapToResponse(payment);
    }

    /**
     * Verifica existência por transactionId.
     *
     * @param transactionId transactionId
     * @return true se existir
     */
    public boolean existsByTransactionId(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            throw new ApiException(ApiErrorCode.INVALID_TRANSACTION_ID, "transactionId é obrigatório", 400);
        }
        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.existsByTransactionId(transactionId.trim())
        );
    }

    /**
     * Lista pagamentos por validade e status.
     *
     * @param date data
     * @param status status
     * @return lista
     */
    public List<PaymentResponse> getPaymentsByValidUntilBeforeAndStatus(Instant date, PaymentStatus status) {
        if (date == null) {
            throw new ApiException(ApiErrorCode.INVALID_DATE, "date é obrigatório", 400);
        }
        if (status == null) {
            throw new ApiException(ApiErrorCode.INVALID_STATUS, "status é obrigatório", 400);
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByValidUntilBeforeAndStatus(date, status)
                        .stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    /**
     * Lista pagamentos concluídos por conta.
     *
     * @param accountId id da conta
     * @return lista
     */
    public List<PaymentResponse> getCompletedPaymentsByAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findCompletedPaymentsByAccount(accountId)
                        .stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    /**
     * Lista pagamentos no período.
     *
     * @param startDate início
     * @param endDate fim
     * @return lista
     */
    public List<PaymentResponse> getPaymentsInPeriod(Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null) {
            throw new ApiException(ApiErrorCode.INVALID_DATE_RANGE, "startDate e endDate são obrigatórios", 400);
        }
        if (endDate.isBefore(startDate)) {
            throw new ApiException(ApiErrorCode.INVALID_DATE_RANGE, "endDate deve ser >= startDate", 400);
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findPaymentsInPeriod(startDate, endDate)
                        .stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    // =========================================================
    // Domain-ish helpers
    // =========================================================

    /**
     * Completa um pagamento por id.
     *
     * <p>Se o pagamento for de upgrade de plano, o upgrade aprovado é aplicado
     * imediatamente após a conclusão do pagamento.</p>
     *
     * @param paymentId id do pagamento
     * @param now instante atual
     * @return pagamento atualizado
     */
    private Payment completePaymentById(Long paymentId, Instant now) {
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

        Account account = payment.getAccount();
        if (account == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada para o pagamento", 404);
        }

        payment.markAsCompleted(now);
        controlPlanePaymentRepository.save(payment);

        if (payment.requiresPlanBinding()) {
            applyApprovedPlanUpgrade(payment);
        }

        account.setStatus(AccountStatus.ACTIVE);
        account.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil(), now));
        accountRepository.save(account);

        return payment;
    }

    /**
     * Marca pagamento como falho.
     *
     * @param paymentId id do pagamento
     * @param reason motivo
     */
    private void failPaymentById(Long paymentId, String reason) {
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

        payment.markAsFailed(reason);
        controlPlanePaymentRepository.save(payment);
    }

    /**
     * Valida regras gerais de pagamento.
     *
     * @param account conta
     * @param amount valor
     * @param now instante atual
     */
    private void validatePayment(Account account, BigDecimal amount, Instant now) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "Valor do pagamento inválido", 400);
        }

        if (account.isDeleted()) {
            throw new ApiException(ApiErrorCode.ACCOUNT_DELETED, "Conta deletada", 410);
        }

        if (account.isBuiltInAccount()) {
            throw new ApiException(ApiErrorCode.BUILTIN_ACCOUNT_NO_BILLING, "Conta BUILTIN não possui billing", 409);
        }

        boolean hasActive = controlPlanePaymentRepository.existsActivePayment(account.getId(), now);
        if (hasActive) {
            throw new ApiException(ApiErrorCode.PAYMENT_ALREADY_EXISTS, "Já existe um pagamento ativo para esta conta", 409);
        }
    }

    /**
     * Valida coerência entre billing e subscription.
     *
     * @param account conta
     * @param targetPlan plano alvo
     * @param billingCycle ciclo
     * @param purpose finalidade
     */
    private void validateBillingBinding(
            Account account,
            SubscriptionPlan targetPlan,
            BillingCycle billingCycle,
            PaymentPurpose purpose
    ) {
        PaymentPurpose safePurpose = resolvePurpose(purpose);

        if (safePurpose == PaymentPurpose.PLAN_UPGRADE) {
            if (targetPlan == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Pagamento de upgrade exige targetPlan", 400);
            }

            if (billingCycle == null) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Pagamento de upgrade exige billingCycle", 400);
            }

            PlanEligibilityResult preview = accountPlanChangeService.previewChange(
                    new ChangeAccountPlanCommand(
                            account.getId(),
                            targetPlan,
                            "billing_preview",
                            "billing_system",
                            "Pré-validação de upgrade por billing"
                    )
            );

            if (preview.changeType() != PlanChangeType.UPGRADE) {
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "O targetPlan informado não representa um upgrade válido", 409);
            }
        } else if (targetPlan != null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan só pode ser enviado para purpose=PLAN_UPGRADE", 400);
        }
    }

    /**
     * Aplica upgrade aprovado vinculado ao pagamento.
     *
     * @param payment pagamento concluído
     */
    private void applyApprovedPlanUpgrade(Payment payment) {
        if (payment.getTargetPlan() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Pagamento de upgrade sem targetPlan vinculado", 409);
        }

        log.info(
                "Aplicando upgrade aprovado via billing. paymentId={}, accountId={}, targetPlan={}, purpose={}",
                payment.getId(),
                payment.getAccount().getId(),
                payment.getTargetPlan(),
                payment.getPaymentPurpose()
        );

        accountPlanChangeService.applyApprovedUpgrade(
                new ChangeAccountPlanCommand(
                        payment.getAccount().getId(),
                        payment.getTargetPlan(),
                        "billing_payment_completed",
                        "billing_system",
                        "Upgrade aprovado via pagamento " + payment.getId()
                )
        );

        log.info(
                "Upgrade aplicado com sucesso via billing. paymentId={}, accountId={}, targetPlan={}",
                payment.getId(),
                payment.getAccount().getId(),
                payment.getTargetPlan()
        );
    }

    // =========================================================
    // Side-effects / mapping
    // =========================================================

    /**
     * Side-effect de envio de email de suspensão.
     *
     * @param account conta
     * @param reason motivo
     */
    private void sendSuspensionEmail(Account account, String reason) {
        log.info("Enviando email de suspensão para: {}, reason={}", account.getLoginEmail(), reason);
    }

    /**
     * Side-effect de confirmação de pagamento.
     *
     * @param account conta
     * @param payment pagamento
     */
    private void sendPaymentConfirmationEmail(Account account, Payment payment) {
        log.info(
                "Enviando confirmação de pagamento para: {}, paymentId={}, purpose={}, targetPlan={}",
                account.getLoginEmail(),
                payment.getId(),
                payment.getPaymentPurpose(),
                payment.getTargetPlan()
        );
    }

    /**
     * Calcula próxima data de vencimento.
     *
     * @param validUntil validade
     * @param now instante atual
     * @return due date
     */
    private LocalDate calculateNextDueDate(Instant validUntil, Instant now) {
        Instant base = (validUntil != null ? validUntil : now.plusSeconds(30L * 24 * 3600));
        return LocalDate.ofInstant(base, ZoneOffset.UTC);
    }

    /**
     * Stub de processamento no gateway.
     *
     * @param paymentId id do pagamento
     * @param paymentRequest request
     * @return true se sucesso
     */
    private boolean processWithPaymentGateway(Long paymentId, PaymentRequest paymentRequest) {
        log.info(
                "Processando pagamento no gateway. paymentId={}, gateway={}, purpose={}, targetPlan={}",
                paymentId,
                paymentRequest.paymentGateway(),
                paymentRequest.purpose(),
                paymentRequest.targetPlan()
        );

        try {
            Thread.sleep(1000);
            return Math.random() < 0.9;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erro ao processar pagamento no gateway. paymentId={}", paymentId, e);
            return false;
        }
    }

    /**
     * Mapeia entidade para DTO.
     *
     * @param payment entidade
     * @return response
     */
    private PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse(
                payment.getId(),
                payment.getAccount().getId(),

                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getStatus(),

                payment.getDescription(),

                payment.getTargetPlan(),
                payment.getBillingCycle(),
                payment.getPaymentPurpose(),
                payment.getPlanPriceSnapshot(),
                payment.getCurrency(),
                payment.getEffectiveFrom(),
                payment.getCoverageEndDate(),

                payment.getPaymentDate(),
                payment.getValidUntil(),
                payment.getRefundedAt(),

                payment.getAudit() != null ? payment.getAudit().getCreatedAt() : null,
                payment.getAudit() != null ? payment.getAudit().getUpdatedAt() : null
        );

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            sendPaymentConfirmationEmail(payment.getAccount(), payment);
        }

        return response;
    }

    // =========================================================
    // Audit helper
    // =========================================================

    /**
     * Registra outcome para ApiException.
     *
     * @param type tipo da ação
     * @param accountId id da conta
     * @param accountEmail email da conta
     * @param details detalhes
     * @param ex exceção
     */
    private void recordOutcomeForApiException(
            SecurityAuditActionType type,
            Long accountId,
            String accountEmail,
            Map<String, Object> details,
            ApiException ex
    ) {
        details.put("error", ex.getError());
        details.put("status", ex.getStatus());

        if (ex.getStatus() == 401 || ex.getStatus() == 403) {
            billingAudit.recordDenied(type, accountId, accountEmail, details);
        } else {
            billingAudit.recordFailure(type, accountId, accountEmail, details);
        }
    }

    /**
     * Resolve purpose com default.
     *
     * @param purpose purpose informado
     * @return purpose resolvido
     */
    private PaymentPurpose resolvePurpose(PaymentPurpose purpose) {
        return purpose != null ? purpose : PaymentPurpose.OTHER;
    }

    /**
     * Resolve ciclo com default.
     *
     * @param billingCycle ciclo informado
     * @return ciclo resolvido
     */
    private BillingCycle resolveBillingCycle(BillingCycle billingCycle) {
        return billingCycle != null ? billingCycle : BillingCycle.ONE_TIME;
    }

    /**
     * Resolve snapshot de preço.
     *
     * @param provided snapshot informado
     * @param fallback fallback
     * @return valor resolvido
     */
    private BigDecimal resolvePlanPriceSnapshot(BigDecimal provided, BigDecimal fallback) {
        return provided != null ? provided : fallback;
    }

    /**
     * Resolve currency code com default BRL.
     *
     * @param currencyCode moeda informada
     * @return moeda resolvida
     */
    private String resolveCurrencyCode(String currencyCode) {
        return StringUtils.hasText(currencyCode) ? currencyCode.trim().toUpperCase() : "BRL";
    }

    /**
     * Normaliza texto opcional.
     *
     * @param value valor
     * @return texto normalizado
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Snapshot interno de criação inicial do pagamento.
     *
     * @param paymentId id do pagamento
     * @param accountEmail email da conta
     */
    private record PaymentCreatedSnapshot(Long paymentId, String accountEmail) {
    }
}