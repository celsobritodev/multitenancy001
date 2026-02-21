package brito.com.multitenancy001.controlplane.billing.app;

import brito.com.multitenancy001.controlplane.accounts.app.AccountStatusService;
import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
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
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
 * Regras:
 * - Controller NÃO grava auditoria; auditoria é responsabilidade do AppService.
 * - Auditoria SOC2-like:
 *   - create payment -> PAYMENT_CREATED (ATTEMPT/SUCCESS/DENIED/FAILURE)
 *   - status change -> PAYMENT_STATUS_CHANGED (ATTEMPT/SUCCESS/DENIED/FAILURE)
 * - Details SEMPRE estruturado (Map/record), nunca string montada na unha.
 * - Nunca registrar segredos (token gateway, dados sensíveis).
 * - Fonte de tempo única: AppClock (Instant).
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

    private final ControlPlaneBillingSecurityAuditRecorder billingAudit;

    // =========================================================
    // Scheduled
    // =========================================================

    @Scheduled(cron = "${app.payment.check-cron:0 0 0 * * *}")
    public void checkPayments() {
        /* Job: aplica regras automáticas de trial expirado, overdue e expiração de pending. */
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

    private void suspendAccountById(Long accountId, String reason) {
        /* Aplica suspensão de account (side-effect + email). */
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

    private void checkExpiredPendingPayments(Instant now) {
        /* Expira pagamentos PENDING antigos (ex.: 30min) e audita mudança de status. */
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
        });
    }

    // =========================================================
    // Commands
    // =========================================================

    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {
        /* Cria pagamento para uma account (admin) e processa gateway. */
        if (adminPaymentRequest == null || adminPaymentRequest.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Instant now = appClock.instant();

        // Pré-audit: create payment attempt
        Map<String, Object> createDetails = billingAudit.baseDetails("payment_create_admin", adminPaymentRequest.accountId(), null);
        createDetails.put("amount", adminPaymentRequest.amount());
        createDetails.put("paymentMethod", adminPaymentRequest.paymentMethod());
        createDetails.put("paymentGateway", adminPaymentRequest.paymentGateway());
        createDetails.put("description", adminPaymentRequest.description());

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), null, createDetails);

        Long paymentId;
        String accountEmail;

        try {
            PaymentCreatedSnapshot snap = publicSchemaUnitOfWork.tx(() -> {
                Account account = accountRepository.findById(adminPaymentRequest.accountId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

                validatePayment(account, adminPaymentRequest.amount(), now);

                Payment payment = Payment.builder()
                        .account(account)
                        .amount(adminPaymentRequest.amount())
                        .paymentMethod(adminPaymentRequest.paymentMethod())
                        .paymentGateway(adminPaymentRequest.paymentGateway())
                        .description(adminPaymentRequest.description())
                        .status(PaymentStatus.PENDING)
                        .paymentDate(now)
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
                        adminPaymentRequest.description()
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

        // Falhou gateway: status -> FAILED (auditar)
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

    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        /* Cria pagamento para a account do usuário logado e processa gateway. */
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

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, createDetails);

        Long paymentId;
        String accountEmail;

        try {
            PaymentCreatedSnapshot snap = publicSchemaUnitOfWork.tx(() -> {
                Account account = accountRepository.findById(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

                validatePayment(account, paymentRequest.amount(), now);

                Payment payment = Payment.builder()
                        .account(account)
                        .amount(paymentRequest.amount())
                        .paymentMethod(paymentRequest.paymentMethod())
                        .paymentGateway(paymentRequest.paymentGateway())
                        .description(paymentRequest.description())
                        .status(PaymentStatus.PENDING)
                        .paymentDate(now)
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

    public PaymentResponse completePaymentManually(Long paymentId) {
        /* Completa um pagamento PENDING manualmente e audita mudança de status. */
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

    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {
        /* Reembolso (parcial/total) e audita mudança de status (ou evento de refund). */
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
        details.put("toStatus", "REFUNDED_OR_PARTIAL"); // sem depender de enum específico

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

    public PaymentResponse getPaymentByIdForMyAccount(Long paymentId) {
        /* Query: pagamento por id escopado à account do usuário logado. */
        Long accountId = requestIdentity.getCurrentAccountId();

        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findScopedByIdAndAccountId(paymentId, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );

        return mapToResponse(payment);
    }

    public List<PaymentResponse> getPaymentsByMyAccount() {
        /* Query: lista pagamentos da account do usuário logado. */
        Long accountId = requestIdentity.getCurrentAccountId();
        return getPaymentsByAccount(accountId);
    }

    public boolean hasActivePaymentMyAccount() {
        /* Query: verifica pagamento ativo para a account do usuário logado. */
        Long accountId = requestIdentity.getCurrentAccountId();
        return hasActivePayment(accountId);
    }

    public List<PaymentResponse> getPaymentsByAccount(Long accountId) {
        /* Query: lista pagamentos por accountId. */
        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByAccount_Id(accountId)
                        .stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
        );
    }

    public boolean hasActivePayment(Long accountId) {
        /* Query: existe pagamento ativo? */
        Instant now = appClock.instant();
        return publicSchemaUnitOfWork.readOnly(() -> controlPlanePaymentRepository.existsActivePayment(accountId, now));
    }

    public PaymentResponse getPaymentById(Long paymentId) {
        /* Query: pagamento por id (admin). */
        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );
        return mapToResponse(payment);
    }

    public BigDecimal getTotalRevenue(Instant startDate, Instant endDate) {
        /* Query: receita agregada no período. */
        List<Object[]> revenueByAccount = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.getRevenueByAccount(startDate, endDate)
        );

        return revenueByAccount.stream()
                .map(obj -> (BigDecimal) obj[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean paymentExistsForAccount(Long paymentId, Long accountId) {
        /* Query: verifica existência de paymentId para accountId. */
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

    public List<PaymentResponse> getPaymentsByAccountAndStatus(Long accountId, PaymentStatus status) {
        /* Query: lista pagamentos por accountId + status. */
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

    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        /* Query: pagamento por transactionId. */
        if (transactionId == null || transactionId.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_TRANSACTION_ID, "transactionId é obrigatório", 400);
        }

        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByTransactionId(transactionId.trim())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );

        return mapToResponse(payment);
    }

    public boolean existsByTransactionId(String transactionId) {
        /* Query: existe payment por transactionId? */
        if (transactionId == null || transactionId.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_TRANSACTION_ID, "transactionId é obrigatório", 400);
        }
        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.existsByTransactionId(transactionId.trim())
        );
    }

    public List<PaymentResponse> getPaymentsByValidUntilBeforeAndStatus(Instant date, PaymentStatus status) {
        /* Query: lista pagamentos por validUntil < date e status. */
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

    public List<PaymentResponse> getCompletedPaymentsByAccount(Long accountId) {
        /* Query: lista pagamentos completados por accountId. */
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

    public List<PaymentResponse> getPaymentsInPeriod(Instant startDate, Instant endDate) {
        /* Query: lista pagamentos no período. */
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

    private Payment completePaymentById(Long paymentId, Instant now) {
        /* Domain helper: marca payment como COMPLETED e ativa account (paymentDueDate). */
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

        Account account = payment.getAccount();
        if (account == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada para o pagamento", 404);
        }

        payment.markAsCompleted(now);
        controlPlanePaymentRepository.save(payment);

        account.setStatus(AccountStatus.ACTIVE);
        account.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil(), now));
        accountRepository.save(account);

        return payment;
    }

    private void failPaymentById(Long paymentId, String reason) {
        /* Domain helper: marca payment como FAILED com reason (sem segredos). */
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

        payment.markAsFailed(reason);
        controlPlanePaymentRepository.save(payment);
    }

    private void validatePayment(Account account, BigDecimal amount, Instant now) {
        /* Valida regras de pagamento (valor, conta, duplicidade de pagamento ativo). */
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

    // =========================================================
    // Side-effects / mapping
    // =========================================================

    private void sendSuspensionEmail(Account account, String reason) {
        /* Side-effect: notificação de suspensão. */
        log.info("Enviando email de suspensão para: {}", account.getLoginEmail());
    }

    private void sendPaymentConfirmationEmail(Account account, Payment payment) {
        /* Side-effect: confirmação de pagamento. */
        log.info("Enviando confirmação de pagamento para: {}", account.getLoginEmail());
    }

    private LocalDate calculateNextDueDate(Instant validUntil, Instant now) {
        /* Calcula próxima data de vencimento como data civil UTC (LocalDate <-> DATE). */
        Instant base = (validUntil != null ? validUntil : now.plusSeconds(30L * 24 * 3600));
        return LocalDate.ofInstant(base, ZoneOffset.UTC);
    }

    private boolean processWithPaymentGateway(Long paymentId, PaymentRequest paymentRequest) {
        /* Stub do gateway (simulado). Não logar dados sensíveis. */
        log.info("Processando pagamento={} com gateway: {}", paymentId, paymentRequest.paymentGateway());

        try {
            Thread.sleep(1000);
            return Math.random() < 0.9;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erro ao processar pagamento no gateway", e);
            return false;
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
        /* Mapeia entidade Payment -> DTO de resposta. */
        PaymentResponse response = new PaymentResponse(
                payment.getId(),
                payment.getAccount().getId(),

                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getStatus(),

                payment.getDescription(),

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

    private void recordOutcomeForApiException(SecurityAuditActionType type,
                                              Long accountId,
                                              String accountEmail,
                                              Map<String, Object> details,
                                              ApiException ex) {
        /* Aplica regra: 401/403 -> DENIED; demais -> FAILURE (com error/status). */
        details.put("error", ex.getError());
        details.put("status", ex.getStatus());

        if (ex.getStatus() == 401 || ex.getStatus() == 403) {
            billingAudit.recordDenied(type, accountId, accountEmail, details);
        } else {
            billingAudit.recordFailure(type, accountId, accountEmail, details);
        }
    }

    // =========================================================
    // Small internal DTO
    // =========================================================

    private record PaymentCreatedSnapshot(Long paymentId, String accountEmail) {}
}