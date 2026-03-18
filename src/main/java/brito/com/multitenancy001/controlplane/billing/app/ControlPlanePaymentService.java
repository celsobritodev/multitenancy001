package brito.com.multitenancy001.controlplane.billing.app;

import brito.com.multitenancy001.controlplane.accounts.app.AccountStatusService;
import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.ChangeAccountPlanCommand;
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
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Serviço de aplicação responsável pelo ciclo de vida de pagamentos do Control Plane.
 *
 * <p>Responsabilidades principais:</p>
 * <ul>
 *   <li>Criar pagamentos administrativos e self-service.</li>
 *   <li>Completar, expirar e falhar pagamentos.</li>
 *   <li>Ativar/suspender contas por eventos de billing.</li>
 *   <li>Enfileirar e processar upgrades de plano de forma assíncrona.</li>
 * </ul>
 *
 * <p>Observação arquitetural crítica:</p>
 * <ul>
 *   <li>Este service roda majoritariamente em contexto PUBLIC.</li>
 *   <li>Durante transações PUBLIC, este service NÃO pode recalcular preview tenant-aware.</li>
 *   <li>A elegibilidade completa do upgrade deve ser calculada anteriormente
 *       pelos serviços de subscription/query apropriados.</li>
 *   <li>Aqui, o billing binding faz apenas validações públicas e estruturais.</li>
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

    /**
     * Fila em memória para processamento assíncrono de upgrades aprovados.
     *
     * <p>Fluxo:</p>
     * <ul>
     *   <li>Pagamento de upgrade é criado e completado em PUBLIC.</li>
     *   <li>Após conclusão, o paymentId entra na fila.</li>
     *   <li>O scheduler processa o binding do plano em transação isolada.</li>
     * </ul>
     */
    private final ConcurrentLinkedQueue<Long> pendingUpgrades = new ConcurrentLinkedQueue<>();

    // =========================================================
    // Scheduled
    // =========================================================

    /**
     * Job periódico de verificação de pagamentos e trial expirado.
     */
    @Scheduled(cron = "${app.payment.check-cron:0 0 0 * * *}")
    public void checkPayments() {
        log.info("Iniciando verificação de pagamentos...");
        Instant now = appClock.instant();

        List<Long> expiredTrialIds = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findExpiredTrialIdsNotDeleted(now, AccountStatus.FREE_TRIAL)
        );

        log.info("Trials expirados encontrados: {}", expiredTrialIds.size());

        for (Long accountId : expiredTrialIds) {
            suspendAccountById(accountId, "Trial expirado");
        }

        LocalDate todayUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);

        List<Long> overdueAccountIds = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findOverdueAccountIdsNotDeleted(AccountStatus.ACTIVE, todayUtc)
        );

        log.info("Contas com pagamento atrasado encontradas: {}", overdueAccountIds.size());

        for (Long accountId : overdueAccountIds) {
            suspendAccountById(accountId, "Pagamento atrasado");
        }

        checkExpiredPendingPayments(now);
        log.info("Verificação de pagamentos concluída.");
    }

    /**
     * Job que processa upgrades pendentes em transações isoladas.
     */
    @Scheduled(fixedDelay = 60000)
    public void processPendingUpgrades() {
        log.info("Processando upgrades pendentes. Fila atual: {} itens", pendingUpgrades.size());

        Long paymentId;
        while ((paymentId = pendingUpgrades.poll()) != null) {
            try {
                log.info("Iniciando processamento de upgrade pendente. paymentId={}", paymentId);
                processUpgradeInIsolatedTransaction(paymentId);
            } catch (Exception e) {
                log.error("Erro ao processar upgrade pendente. paymentId={}", paymentId, e);
            }
        }

        log.info("Processamento de upgrades pendentes concluído.");
    }

    /**
     * Suspende uma conta por motivo de billing.
     *
     * @param accountId id da conta
     * @param reason motivo da suspensão
     */
    private void suspendAccountById(Long accountId, String reason) {
        log.info("Suspendendo conta por billing. accountId={}, reason={}", accountId, reason);

        accountStatusService.changeAccountStatus(
                accountId,
                new AccountStatusChangeCommand(AccountStatus.SUSPENDED, reason, "billing_job")
        );

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findAnyById(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404))
        );

        sendSuspensionEmail(account, reason);
        log.info("Conta suspensa com sucesso. accountId={}, email={}", accountId, account.getLoginEmail());
    }

    /**
     * Expira pagamentos pendentes antigos.
     *
     * @param now instante atual
     */
    private void checkExpiredPendingPayments(Instant now) {
        Instant thirtyMinutesAgo = now.minusSeconds(30 * 60);

        log.info("Verificando pagamentos PENDING expirados antes de {}", thirtyMinutesAgo);

        publicSchemaUnitOfWork.tx(() -> {
            List<Payment> expiredPayments = controlPlanePaymentRepository
                    .findByStatusAndAudit_CreatedAtBefore(PaymentStatus.PENDING, thirtyMinutesAgo);

            log.info("Pagamentos pendentes expirados encontrados: {}", expiredPayments.size());

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
                    log.info("Expirando pagamento pendente. paymentId={}, accountId={}", payment.getId(), accountId);
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
    // Commands - Admin
    // =========================================================

    /**
     * Processa pagamento administrativo para uma conta específica.
     *
     * @param adminPaymentRequest request administrativo
     * @return resposta final do pagamento
     */
    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {
        log.info("========== processPaymentForAccount INICIADO ==========");

        if (adminPaymentRequest == null || adminPaymentRequest.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info(
                "processPaymentForAccount request: accountId={}, amount={}, purpose={}, targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}",
                adminPaymentRequest.accountId(),
                adminPaymentRequest.amount(),
                adminPaymentRequest.purpose(),
                adminPaymentRequest.targetPlan(),
                adminPaymentRequest.billingCycle(),
                adminPaymentRequest.paymentMethod(),
                adminPaymentRequest.paymentGateway()
        );

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
                log.info("TX1 PUBLIC: criando pagamento administrativo. accountId={}", adminPaymentRequest.accountId());

                Account account = accountRepository.findById(adminPaymentRequest.accountId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

                validatePayment(account, adminPaymentRequest.amount(), now, adminPaymentRequest.purpose());
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
                log.info("TX1 PUBLIC: pagamento administrativo criado com sucesso. paymentId={}, accountId={}", saved.getId(), account.getId());
                return new PaymentCreatedSnapshot(saved.getId(), account.getLoginEmail());
            });

            paymentId = snap.paymentId();
            accountEmail = snap.accountEmail();

            createDetails.put("paymentId", paymentId);
            createDetails.put("status", PaymentStatus.PENDING.name());

            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), accountEmail, createDetails);
            log.info("Pagamento administrativo criado com sucesso. paymentId={}, accountEmail={}", paymentId, accountEmail);

        } catch (ApiException ex) {
            log.error("ApiException na criação do pagamento administrativo. accountId={}, msg={}", adminPaymentRequest.accountId(), ex.getMessage(), ex);
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), null, createDetails, ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Exception inesperada na criação do pagamento administrativo. accountId={}", adminPaymentRequest.accountId(), ex);
            createDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, adminPaymentRequest.accountId(), null, createDetails);
            throw ex;
        }

        log.info("Enviando pagamento administrativo ao gateway. paymentId={}", paymentId);

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

        log.info("Gateway retornou para pagamento administrativo. paymentId={}, approved={}", paymentId, ok);

        if (ok) {
            log.info("Pagamento administrativo aprovado. paymentId={}", paymentId);

            Map<String, Object> statusDetails = billingAudit.baseDetails("payment_status_change_complete", adminPaymentRequest.accountId(), accountEmail);
            statusDetails.put("paymentId", paymentId);
            statusDetails.put("fromStatus", PaymentStatus.PENDING.name());
            statusDetails.put("toStatus", PaymentStatus.COMPLETED.name());

            billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails);

            try {
                log.info("TX2 REQUIRES_NEW: completando pagamento administrativo. paymentId={}", paymentId);

                Payment payment = publicSchemaUnitOfWork.requiresNew(() -> {
                    log.info("TX2 REQUIRES_NEW: chamando completePaymentById. paymentId={}", paymentId);
                    return completePaymentById(paymentId, now);
                });

                if (payment.getPaymentPurpose() == PaymentPurpose.PLAN_UPGRADE) {
                    log.info("Pagamento requer binding de plano. Adicionando à fila assíncrona. paymentId={}", paymentId);
                    pendingUpgrades.offer(paymentId);
                }

                Payment finalPayment = publicSchemaUnitOfWork.readOnly(() ->
                        controlPlanePaymentRepository.findByIdWithAccount(paymentId)
                                .orElseThrow(() -> new ApiException(
                                        ApiErrorCode.PAYMENT_NOT_FOUND,
                                        "Pagamento não encontrado",
                                        404
                                ))
                );

                billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails);

                log.info("Mapeando resposta final do pagamento administrativo. paymentId={}", paymentId);
                PaymentResponse response = mapToResponse(finalPayment);
                log.info("Resposta final do pagamento administrativo mapeada com sucesso. paymentId={}", paymentId);
                return response;

            } catch (ApiException ex) {
                log.error("ApiException na conclusão do pagamento administrativo. paymentId={}, msg={}", paymentId, ex.getMessage(), ex);
                recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails, ex);
                throw ex;
            } catch (Exception ex) {
                log.error("Exception inesperada na conclusão do pagamento administrativo. paymentId={}", paymentId, ex);
                statusDetails.put("exception", ex.getClass().getSimpleName());
                billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, statusDetails);
                throw ex;
            }
        }

        log.warn("Pagamento administrativo rejeitado pelo gateway. paymentId={}", paymentId);

        Map<String, Object> failDetails = billingAudit.baseDetails("payment_status_change_fail_gateway", adminPaymentRequest.accountId(), accountEmail);
        failDetails.put("paymentId", paymentId);
        failDetails.put("fromStatus", PaymentStatus.PENDING.name());
        failDetails.put("toStatus", PaymentStatus.FAILED.name());
        failDetails.put("reason", "Falha no processamento do pagamento");

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails);

        try {
            log.info("Marcando pagamento administrativo como FAILED. paymentId={}", paymentId);
            publicSchemaUnitOfWork.tx(() -> {
                failPaymentById(paymentId, "Falha no processamento do pagamento");
                return null;
            });
            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails);
            log.info("Pagamento administrativo marcado como FAILED com sucesso. paymentId={}", paymentId);
        } catch (ApiException ex) {
            log.error("ApiException ao marcar pagamento administrativo como FAILED. paymentId={}, msg={}", paymentId, ex.getMessage(), ex);
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails, ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Exception inesperada ao marcar pagamento administrativo como FAILED. paymentId={}", paymentId, ex);
            failDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, adminPaymentRequest.accountId(), accountEmail, failDetails);
            throw ex;
        }

        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento", 402);
    }

    // =========================================================
    // Commands - Self-service
    // =========================================================

    /**
     * Processa pagamento self-service para a conta autenticada.
     *
     * @param paymentRequest request self-service
     * @return resposta final do pagamento
     */
    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        log.info("========== processPaymentForMyAccount INICIADO ==========");

        if (paymentRequest == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido", 400);
        }

        log.info(
                "processPaymentForMyAccount request: amount={}, purpose={}, targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}",
                paymentRequest.amount(),
                paymentRequest.purpose(),
                paymentRequest.targetPlan(),
                paymentRequest.billingCycle(),
                paymentRequest.paymentMethod(),
                paymentRequest.paymentGateway()
        );

        Long accountId = requestIdentity.getCurrentAccountId();
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Usuário não autenticado", 401);
        }

        log.info("Conta autenticada para pagamento self-service. accountId={}", accountId);

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
                log.info("TX1 PUBLIC: criando pagamento self-service. accountId={}", accountId);

                Account account = accountRepository.findById(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

                validatePayment(account, paymentRequest.amount(), now, paymentRequest.purpose());
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
                log.info("TX1 PUBLIC: pagamento self-service criado com sucesso. paymentId={}, accountId={}", saved.getId(), account.getId());
                return new PaymentCreatedSnapshot(saved.getId(), account.getLoginEmail());
            });

            paymentId = snap.paymentId();
            accountEmail = snap.accountEmail();

            createDetails.put("paymentId", paymentId);
            createDetails.put("status", PaymentStatus.PENDING.name());

            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_CREATED, accountId, accountEmail, createDetails);
            log.info("Pagamento self-service criado com sucesso. paymentId={}, accountEmail={}", paymentId, accountEmail);

        } catch (ApiException ex) {
            log.error("ApiException na criação do pagamento self-service. accountId={}, msg={}", accountId, ex.getMessage(), ex);
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, createDetails, ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Exception inesperada na criação do pagamento self-service. accountId={}", accountId, ex);
            createDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_CREATED, accountId, null, createDetails);
            throw ex;
        }

        log.info("Enviando pagamento self-service ao gateway. paymentId={}", paymentId);
        boolean ok = processWithPaymentGateway(paymentId, paymentRequest);
        log.info("Gateway retornou para pagamento self-service. paymentId={}, approved={}", paymentId, ok);

        if (ok) {
            log.info("Pagamento self-service aprovado. paymentId={}", paymentId);

            Map<String, Object> statusDetails = billingAudit.baseDetails("payment_status_change_complete", accountId, accountEmail);
            statusDetails.put("paymentId", paymentId);
            statusDetails.put("fromStatus", PaymentStatus.PENDING.name());
            statusDetails.put("toStatus", PaymentStatus.COMPLETED.name());

            billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails);

            try {
                log.info("TX2 REQUIRES_NEW: completando pagamento self-service. paymentId={}", paymentId);

                Payment payment = publicSchemaUnitOfWork.requiresNew(() -> {
                    log.info("TX2 REQUIRES_NEW: chamando completePaymentById. paymentId={}", paymentId);
                    return completePaymentById(paymentId, now);
                });

                if (payment.getPaymentPurpose() == PaymentPurpose.PLAN_UPGRADE) {
                    log.info("Pagamento requer binding de plano. Adicionando à fila assíncrona. paymentId={}", paymentId);
                    pendingUpgrades.offer(paymentId);
                }

                Payment finalPayment = publicSchemaUnitOfWork.readOnly(() ->
                        controlPlanePaymentRepository.findByIdWithAccount(paymentId)
                                .orElseThrow(() -> new ApiException(
                                        ApiErrorCode.PAYMENT_NOT_FOUND,
                                        "Pagamento não encontrado",
                                        404
                                ))
                );

                billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails);

                log.info("Mapeando resposta final do pagamento self-service. paymentId={}", paymentId);
                PaymentResponse response = mapToResponse(finalPayment);
                log.info("Resposta final do pagamento self-service mapeada com sucesso. paymentId={}", paymentId);
                return response;

            } catch (ApiException ex) {
                log.error("ApiException na conclusão do pagamento self-service. paymentId={}, msg={}", paymentId, ex.getMessage(), ex);
                recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails, ex);
                throw ex;
            } catch (Exception ex) {
                log.error("Exception inesperada na conclusão do pagamento self-service. paymentId={}", paymentId, ex);
                statusDetails.put("exception", ex.getClass().getSimpleName());
                billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, statusDetails);
                throw ex;
            }
        }

        log.warn("Pagamento self-service rejeitado pelo gateway. paymentId={}", paymentId);

        Map<String, Object> failDetails = billingAudit.baseDetails("payment_status_change_fail_gateway", accountId, accountEmail);
        failDetails.put("paymentId", paymentId);
        failDetails.put("fromStatus", PaymentStatus.PENDING.name());
        failDetails.put("toStatus", PaymentStatus.FAILED.name());
        failDetails.put("reason", "Falha no processamento do pagamento");

        billingAudit.recordAttempt(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails);

        try {
            log.info("Marcando pagamento self-service como FAILED. paymentId={}", paymentId);
            publicSchemaUnitOfWork.tx(() -> {
                failPaymentById(paymentId, "Falha no processamento do pagamento");
                return null;
            });
            billingAudit.recordSuccess(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails);
            log.info("Pagamento self-service marcado como FAILED com sucesso. paymentId={}", paymentId);
        } catch (ApiException ex) {
            log.error("ApiException ao marcar pagamento self-service como FAILED. paymentId={}, msg={}", paymentId, ex.getMessage(), ex);
            recordOutcomeForApiException(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails, ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Exception inesperada ao marcar pagamento self-service como FAILED. paymentId={}", paymentId, ex);
            failDetails.put("exception", ex.getClass().getSimpleName());
            billingAudit.recordFailure(SecurityAuditActionType.PAYMENT_STATUS_CHANGED, accountId, accountEmail, failDetails);
            throw ex;
        }

        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento", 402);
    }

    /**
     * Processa upgrade em transações isoladas após a conclusão do pagamento.
     *
     * @param paymentId id do pagamento
     */
    private void processUpgradeInIsolatedTransaction(Long paymentId) {
        log.info(">>> processUpgradeInIsolatedTransaction. paymentId={}", paymentId);

        try {
            Payment payment = publicSchemaUnitOfWork.requiresNew(() -> {
                log.info("TX ISOLADA 1: carregando pagamento para binding. paymentId={}", paymentId);
                return controlPlanePaymentRepository.findByIdWithAccount(paymentId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.PAYMENT_NOT_FOUND,
                                "Pagamento não encontrado",
                                404
                        ));
            });

            if (!payment.requiresPlanBinding()) {
                log.info("Pagamento não requer binding de plano. paymentId={}, purpose={}", paymentId, payment.getPaymentPurpose());
                return;
            }

            publicSchemaUnitOfWork.requiresNew(() -> {
                log.info("TX ISOLADA 2: aplicando upgrade de plano. paymentId={}", paymentId);
                applyApprovedPlanUpgrade(payment);
                return null;
            });

            log.info("Upgrade processado com sucesso em transação isolada. paymentId={}", paymentId);

        } catch (Exception e) {
            log.error("Erro no processamento assíncrono de upgrade. paymentId={}", paymentId, e);
        }

        log.info("<<< processUpgradeInIsolatedTransaction concluído. paymentId={}", paymentId);
    }

    /**
     * Completa um pagamento e ativa a conta no contexto PUBLIC.
     *
     * @param paymentId id do pagamento
     * @param now instante atual
     * @return pagamento atualizado
     */
    private Payment completePaymentById(Long paymentId, Instant now) {
        log.info(">>> completePaymentById. paymentId={}", paymentId);

        Payment payment = controlPlanePaymentRepository.findByIdWithAccount(paymentId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.PAYMENT_NOT_FOUND,
                        "Pagamento não encontrado",
                        404
                ));

        Account account = payment.getAccount();
        if (account == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_NOT_FOUND,
                    "Conta não encontrada para o pagamento",
                    404
            );
        }

        log.info(
                "Completando pagamento. paymentId={}, accountId={}, currentStatus={}, currentPlan={}, validUntil={}",
                paymentId,
                account.getId(),
                account.getStatus(),
                account.getSubscriptionPlan(),
                payment.getValidUntil()
        );

        payment.markAsCompleted(now);
        payment = controlPlanePaymentRepository.save(payment);

        Account refreshedAccount = accountRepository.findById(account.getId())
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada para atualização pós-pagamento",
                        404
                ));

        refreshedAccount.setStatus(AccountStatus.ACTIVE);
        refreshedAccount.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil(), now));
        accountRepository.save(refreshedAccount);

        log.info(
                "<<< completePaymentById concluído. paymentId={}, accountId={}, newStatus={}, paymentDueDate={}, currentPlan={}",
                paymentId,
                refreshedAccount.getId(),
                refreshedAccount.getStatus(),
                refreshedAccount.getPaymentDueDate(),
                refreshedAccount.getSubscriptionPlan()
        );

        return payment;
    }

    /**
     * Aplica o upgrade de plano após o pagamento aprovado.
     *
     * @param payment pagamento concluído
     */
    private void applyApprovedPlanUpgrade(Payment payment) {
        log.info(">>> applyApprovedPlanUpgrade. paymentId={}, targetPlan={}", payment.getId(), payment.getTargetPlan());

        if (payment.getTargetPlan() == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Pagamento de upgrade sem targetPlan vinculado",
                    409
            );
        }

        if (payment.getAccount() == null || payment.getAccount().getId() == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_NOT_FOUND,
                    "Pagamento sem conta válida para aplicação de upgrade",
                    404
            );
        }

        log.info(
                "Aplicando upgrade aprovado. paymentId={}, accountId={}, currentPlan={}, targetPlan={}",
                payment.getId(),
                payment.getAccount().getId(),
                payment.getAccount().getSubscriptionPlan(),
                payment.getTargetPlan()
        );

        try {
            accountPlanChangeService.applyApprovedUpgrade(
                    new ChangeAccountPlanCommand(
                            payment.getAccount().getId(),
                            payment.getTargetPlan(),
                            "billing_payment_completed",
                            "billing_system",
                            "Upgrade aprovado via pagamento " + payment.getId()
                    )
            );
            log.info("Upgrade aplicado com sucesso pelo AccountPlanChangeService. paymentId={}", payment.getId());
        } catch (Exception e) {
            log.error("Erro ao aplicar upgrade no AccountPlanChangeService. paymentId={}", payment.getId(), e);
            throw e;
        }

        Account updatedAccount = accountRepository.findById(payment.getAccount().getId())
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada após aplicação do upgrade",
                        404
                ));

        log.info(
                "<<< applyApprovedPlanUpgrade concluído. paymentId={}, accountId={}, newPlan={}",
                payment.getId(),
                updatedAccount.getId(),
                updatedAccount.getSubscriptionPlan()
        );
    }

    /**
     * Valida o binding mínimo de billing sem recalcular preview tenant-aware.
     *
     * <p>Importante:</p>
     * <ul>
     *   <li>Este método pode ser chamado dentro de transação PUBLIC.</li>
     *   <li>Por isso, ele NÃO pode chamar serviços que entrem no schema TENANT.</li>
     *   <li>O preview completo de elegibilidade deve ter sido executado antes,
     *       fora da transação PUBLIC, pelos services de subscription.</li>
     * </ul>
     *
     * @param account conta alvo
     * @param targetPlan plano alvo informado
     * @param billingCycle ciclo informado
     * @param purpose propósito do pagamento
     */
    private void validateBillingBinding(
            Account account,
            SubscriptionPlan targetPlan,
            BillingCycle billingCycle,
            PaymentPurpose purpose
    ) {
        log.info(
                "validateBillingBinding INICIO: accountId={}, currentPlan={}, targetPlan={}, billingCycle={}, purpose={}",
                account != null ? account.getId() : null,
                account != null ? account.getSubscriptionPlan() : null,
                targetPlan,
                billingCycle,
                purpose
        );

        PaymentPurpose safePurpose = resolvePurpose(purpose);

        if (safePurpose == PaymentPurpose.PLAN_UPGRADE) {
            if (targetPlan == null) {
                log.warn("Billing binding inválido: upgrade sem targetPlan. accountId={}", account != null ? account.getId() : null);
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Pagamento de upgrade exige targetPlan", 400);
            }

            if (billingCycle == null) {
                log.warn("Billing binding inválido: upgrade sem billingCycle. accountId={}, targetPlan={}",
                        account != null ? account.getId() : null, targetPlan);
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Pagamento de upgrade exige billingCycle", 400);
            }

            if (account == null) {
                log.warn("Billing binding inválido: account nula para upgrade.");
                throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada para billing binding", 404);
            }

            if (account.getSubscriptionPlan() == null) {
                log.warn("Billing binding inválido: conta sem plano atual. accountId={}", account.getId());
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Conta sem plano atual para validar upgrade", 409);
            }

            if (account.getSubscriptionPlan() == targetPlan) {
                log.warn(
                        "Billing binding inválido: targetPlan não representa upgrade. accountId={}, currentPlan={}, targetPlan={}",
                        account.getId(),
                        account.getSubscriptionPlan(),
                        targetPlan
                );
                throw new ApiException(ApiErrorCode.INVALID_REQUEST, "O targetPlan informado não representa um upgrade válido", 409);
            }

            log.info(
                    "Validação pública de billing binding concluída com sucesso. accountId={}, currentPlan={}, targetPlan={}, billingCycle={}",
                    account.getId(),
                    account.getSubscriptionPlan(),
                    targetPlan,
                    billingCycle
            );
            return;
        }

        if (targetPlan != null) {
            log.warn(
                    "Billing binding inválido: targetPlan informado para purpose sem upgrade. accountId={}, purpose={}, targetPlan={}",
                    account != null ? account.getId() : null,
                    safePurpose,
                    targetPlan
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan só pode ser enviado para purpose=PLAN_UPGRADE", 400);
        }

        if (billingCycle != null && safePurpose != PaymentPurpose.PLAN_UPGRADE) {
            log.warn(
                    "Billing binding inválido: billingCycle informado para purpose sem upgrade. accountId={}, purpose={}, billingCycle={}",
                    account != null ? account.getId() : null,
                    safePurpose,
                    billingCycle
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "billingCycle só pode ser enviado para purpose=PLAN_UPGRADE", 400);
        }

        log.info(
                "Validação de billing sem binding de plano concluída. accountId={}, purpose={}",
                account != null ? account.getId() : null,
                safePurpose
        );
    }

    /**
     * Valida regras gerais de criação de pagamento.
     *
     * @param account conta alvo
     * @param amount valor
     * @param now instante atual
     * @param purpose propósito do pagamento
     */
    private void validatePayment(
            Account account,
            BigDecimal amount,
            Instant now,
            PaymentPurpose purpose
    ) {
        log.info(
                "validatePayment INICIO: accountId={}, amount={}, now={}, purpose={}",
                account != null ? account.getId() : null,
                amount,
                now,
                purpose
        );

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("validatePayment inválido: amount <= 0. accountId={}, amount={}", account != null ? account.getId() : null, amount);
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "Valor do pagamento inválido", 400);
        }

        if (account.isDeleted()) {
            log.warn("validatePayment inválido: conta deletada. accountId={}", account.getId());
            throw new ApiException(ApiErrorCode.ACCOUNT_DELETED, "Conta deletada", 410);
        }

        if (account.isBuiltInAccount()) {
            log.warn("validatePayment inválido: conta BUILTIN sem billing. accountId={}", account.getId());
            throw new ApiException(ApiErrorCode.BUILTIN_ACCOUNT_NO_BILLING, "Conta BUILTIN não possui billing", 409);
        }

        PaymentPurpose resolvedPurpose = resolvePurpose(purpose);

        if (resolvedPurpose != PaymentPurpose.PLAN_UPGRADE) {
            boolean hasActive = controlPlanePaymentRepository.existsActivePayment(account.getId(), now);
            log.info("Verificando pagamento ativo concorrente. accountId={}, purpose={}, hasActive={}",
                    account.getId(), resolvedPurpose, hasActive);

            if (hasActive) {
                log.warn("validatePayment inválido: já existe pagamento ativo. accountId={}", account.getId());
                throw new ApiException(
                        ApiErrorCode.PAYMENT_ALREADY_EXISTS,
                        "Já existe um pagamento ativo para esta conta",
                        409
                );
            }
        }

        log.info("validatePayment concluído com sucesso. accountId={}", account.getId());
    }

    /**
     * Marca um pagamento como falho.
     *
     * @param paymentId id do pagamento
     * @param reason motivo
     */
    private void failPaymentById(Long paymentId, String reason) {
        log.info("failPaymentById INICIO: paymentId={}, reason={}", paymentId, reason);

        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

        payment.markAsFailed(reason);
        controlPlanePaymentRepository.save(payment);

        log.info("failPaymentById concluído. paymentId={}, newStatus={}", paymentId, payment.getStatus());
    }

    /**
     * Mapeia a entidade Payment para DTO de resposta.
     *
     * @param payment entidade
     * @return dto
     */
    private PaymentResponse mapToResponse(Payment payment) {
        Payment paymentToUse = payment;

        if (paymentToUse.getAccount() == null || paymentToUse.getAccount().getLoginEmail() == null) {
            log.info("Payment sem account hidratada. Recarregando com join. paymentId={}", payment.getId());
            paymentToUse = controlPlanePaymentRepository.findByIdWithAccount(payment.getId())
                    .orElse(payment);
        }

        log.info(
                "Mapeando PaymentResponse. paymentId={}, accountId={}, status={}, targetPlan={}, purpose={}",
                paymentToUse.getId(),
                paymentToUse.getAccount() != null ? paymentToUse.getAccount().getId() : null,
                paymentToUse.getStatus(),
                paymentToUse.getTargetPlan(),
                paymentToUse.getPaymentPurpose()
        );

        return new PaymentResponse(
                paymentToUse.getId(),
                paymentToUse.getAccount().getId(),
                paymentToUse.getAmount(),
                paymentToUse.getPaymentMethod(),
                paymentToUse.getPaymentGateway(),
                paymentToUse.getStatus(),
                paymentToUse.getDescription(),
                paymentToUse.getTargetPlan(),
                paymentToUse.getBillingCycle(),
                paymentToUse.getPaymentPurpose(),
                paymentToUse.getPlanPriceSnapshot(),
                paymentToUse.getCurrency(),
                paymentToUse.getEffectiveFrom(),
                paymentToUse.getCoverageEndDate(),
                paymentToUse.getPaymentDate(),
                paymentToUse.getValidUntil(),
                paymentToUse.getRefundedAt(),
                paymentToUse.getAudit() != null ? paymentToUse.getAudit().getCreatedAt() : null,
                paymentToUse.getAudit() != null ? paymentToUse.getAudit().getUpdatedAt() : null
        );
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Stub atual para e-mail de suspensão.
     *
     * @param account conta
     * @param reason motivo
     */
    private void sendSuspensionEmail(Account account, String reason) {
        log.info("Enviando email de suspensão. accountId={}, email={}, reason={}",
                account.getId(), account.getLoginEmail(), reason);
    }

    /**
     * Calcula a próxima data de vencimento da conta.
     *
     * @param validUntil validade do pagamento
     * @param now instante atual
     * @return data de vencimento
     */
    private LocalDate calculateNextDueDate(Instant validUntil, Instant now) {
        Instant base = (validUntil != null ? validUntil : now.plusSeconds(30L * 24 * 3600));
        LocalDate dueDate = LocalDate.ofInstant(base, ZoneOffset.UTC);

        log.info("calculateNextDueDate: validUntil={}, now={}, dueDate={}", validUntil, now, dueDate);
        return dueDate;
    }

    /**
     * Simula integração com gateway de pagamento.
     *
     * @param paymentId id do pagamento
     * @param paymentRequest request original
     * @return true quando aprovado
     */
    private boolean processWithPaymentGateway(Long paymentId, PaymentRequest paymentRequest) {
        log.info(
                "Processando pagamento no gateway. paymentId={}, amount={}, paymentMethod={}, paymentGateway={}, targetPlan={}, purpose={}",
                paymentId,
                paymentRequest != null ? paymentRequest.amount() : null,
                paymentRequest != null ? paymentRequest.paymentMethod() : null,
                paymentRequest != null ? paymentRequest.paymentGateway() : null,
                paymentRequest != null ? paymentRequest.targetPlan() : null,
                paymentRequest != null ? paymentRequest.purpose() : null
        );

        try {
            Thread.sleep(50);
            log.info("Gateway processado com sucesso. paymentId={}", paymentId);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erro/interrupção ao processar pagamento no gateway. paymentId={}", paymentId, e);
            return false;
        }
    }

    /**
     * Registra outcome de ApiException no audit.
     *
     * @param type tipo de ação
     * @param accountId conta
     * @param accountEmail email
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

        log.warn(
                "Registrando outcome de ApiException. type={}, accountId={}, accountEmail={}, status={}, error={}",
                type,
                accountId,
                accountEmail,
                ex.getStatus(),
                ex.getError()
        );

        if (ex.getStatus() == 401 || ex.getStatus() == 403) {
            billingAudit.recordDenied(type, accountId, accountEmail, details);
        } else {
            billingAudit.recordFailure(type, accountId, accountEmail, details);
        }
    }

    /**
     * Resolve purpose com fallback.
     *
     * @param purpose purpose informado
     * @return purpose final
     */
    private PaymentPurpose resolvePurpose(PaymentPurpose purpose) {
        return purpose != null ? purpose : PaymentPurpose.OTHER;
    }

    /**
     * Resolve billing cycle com fallback.
     *
     * @param billingCycle billingCycle informado
     * @return billingCycle final
     */
    private BillingCycle resolveBillingCycle(BillingCycle billingCycle) {
        return billingCycle != null ? billingCycle : BillingCycle.ONE_TIME;
    }

    /**
     * Resolve snapshot de preço com fallback.
     *
     * @param provided valor informado
     * @param fallback fallback
     * @return valor final
     */
    private BigDecimal resolvePlanPriceSnapshot(BigDecimal provided, BigDecimal fallback) {
        return provided != null ? provided : fallback;
    }

    /**
     * Resolve currency code com fallback.
     *
     * @param currencyCode moeda informada
     * @return moeda final
     */
    private String resolveCurrencyCode(String currencyCode) {
        return StringUtils.hasText(currencyCode) ? currencyCode.trim().toUpperCase() : "BRL";
    }

    /**
     * Normaliza texto opcional.
     *
     * @param value valor bruto
     * @return valor normalizado
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Snapshot interno do pagamento recém-criado.
     *
     * @param paymentId id do pagamento
     * @param accountEmail email da conta
     */
    private record PaymentCreatedSnapshot(Long paymentId, String accountEmail) {
    }
}