package brito.com.multitenancy001.controlplane.billing.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.AccountStatusService;
import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.controlplane.billing.persistence.ControlPlanePaymentRepository;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    private final AccountRepository accountRepository;
    private final ControlPlanePaymentRepository controlPlanePaymentRepository;

    private final SecurityUtils securityUtils;
    private final AppClock appClock;
    private final AccountStatusService accountStatusService;

    // =========================================================
    // Scheduled
    // =========================================================

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

    private void suspendAccountById(Long accountId, String reason) {
        if (accountId == null) return;

        accountStatusService.changeAccountStatus(
                accountId,
                new AccountStatusChangeCommand(AccountStatus.SUSPENDED)
        );

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findAnyById(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada"))
        );

        sendSuspensionEmail(account, reason);
    }

    private void checkExpiredPendingPayments(Instant now) {
        Instant threshold = now.minusSeconds(30L * 60);

        publicSchemaUnitOfWork.tx(() -> {
            List<Payment> expired = controlPlanePaymentRepository
                    .findByStatusAndAudit_CreatedAtBefore(PaymentStatus.PENDING, threshold);

            for (Payment p : expired) {
                p.setStatus(PaymentStatus.EXPIRED);
                controlPlanePaymentRepository.save(p);
            }
            return null;
        });
    }

    // =========================================================
    // Commands
    // =========================================================

    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {
        if (adminPaymentRequest == null || adminPaymentRequest.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }
        if (adminPaymentRequest.amount() == null) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "amount é obrigatório");
        }

        Instant now = appClock.instant();

        Long paymentId = publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findById(adminPaymentRequest.accountId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada"));

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

            return controlPlanePaymentRepository.save(payment).getId();
        });

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
            Payment payment = publicSchemaUnitOfWork.tx(() -> completePaymentById(paymentId, now));
            return mapToResponse(payment);
        }

        publicSchemaUnitOfWork.tx(() -> {
            failPaymentById(paymentId, "Falha no processamento do pagamento");
            return null;
        });

        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento");
    }

    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        if (paymentRequest == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST_BODY, "payload é obrigatório");
        }
        if (paymentRequest.amount() == null) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "amount é obrigatório");
        }

        Long accountId = securityUtils.getCurrentAccountId();
        Instant now = appClock.instant();

        Long paymentId = publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada"));

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

            return controlPlanePaymentRepository.save(payment).getId();
        });

        boolean ok = processWithPaymentGateway(paymentId, paymentRequest);

        if (ok) {
            Payment payment = publicSchemaUnitOfWork.tx(() -> completePaymentById(paymentId, now));
            return mapToResponse(payment);
        }

        publicSchemaUnitOfWork.tx(() -> {
            failPaymentById(paymentId, "Falha no processamento do pagamento");
            return null;
        });

        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento");
    }

    public PaymentResponse completePaymentManually(Long paymentId) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório");
        }

        Instant now = appClock.instant();

        Payment payment = publicSchemaUnitOfWork.tx(() -> {
            Payment p = controlPlanePaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado"));

            if (p.getStatus() != PaymentStatus.PENDING) {
                throw new ApiException(ApiErrorCode.INVALID_PAYMENT_STATUS, "Somente PENDING pode ser completado manualmente");
            }

            return completePaymentById(paymentId, now);
        });

        return mapToResponse(payment);
    }

    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório");
        }

        Instant now = appClock.instant();

        Payment payment = publicSchemaUnitOfWork.tx(() -> {
            Payment p = controlPlanePaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado"));

            if (!p.canBeRefunded(now)) {
                throw new ApiException(ApiErrorCode.PAYMENT_NOT_REFUNDABLE, "Pagamento não pode ser reembolsado");
            }

            // ✅ Ajuste correto: o domínio define a assinatura.
            // Pelo erro do seu compile: refundFully(Instant, String)
            if (amount == null) {
                p.refundFully(now, reason);
            } else {
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "amount deve ser > 0");
                }
                p.refundPartially(now, amount, reason);
            }

            return controlPlanePaymentRepository.save(p);
        });

        return mapToResponse(payment);
    }

    // =========================================================
    // Queries (Controller admin depende dessas assinaturas)
    // =========================================================

    public List<PaymentResponse> getPaymentsByAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByAccount_Id(accountId)
                        .stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    public boolean hasActivePayment(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }

        Instant now = appClock.instant();
        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.existsActivePayment(accountId, now)
        );
    }

    public boolean paymentExistsForAccount(Long paymentId, Long accountId) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório");
        }
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.existsByIdAndAccount_Id(paymentId, accountId)
        );
    }

    public List<PaymentResponse> getCompletedPaymentsByAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório");
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findCompletedPaymentsByAccount(accountId)
                        .stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    public List<PaymentResponse> getPaymentsInPeriod(Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null) {
            throw new ApiException(ApiErrorCode.DATE_RANGE_REQUIRED, "start e end são obrigatórios");
        }
        if (endDate.isBefore(startDate)) {
            throw new ApiException(ApiErrorCode.INVALID_RANGE, "end não pode ser antes de start");
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findPaymentsInPeriod(startDate, endDate)
                        .stream()
                        .map(this::mapToResponse)
                        .toList()
        );
    }

    public BigDecimal getTotalRevenue(Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null) {
            throw new ApiException(ApiErrorCode.DATE_RANGE_REQUIRED, "start e end são obrigatórios");
        }
        if (endDate.isBefore(startDate)) {
            throw new ApiException(ApiErrorCode.INVALID_RANGE, "end não pode ser antes de start");
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.sumRevenueInPeriod(startDate, endDate)
        );
    }

    // =========================================================
    // Domain-ish helpers (executados dentro de tx)
    // =========================================================

    private Payment completePaymentById(Long paymentId, Instant now) {
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado"));

        Account account = payment.getAccount();
        if (account == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta do pagamento não encontrada");
        }

        payment.markAsCompleted(now);
        controlPlanePaymentRepository.save(payment);

        account.setStatus(AccountStatus.ACTIVE);
        account.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil(), now));
        accountRepository.save(account);

        return payment;
    }

    private void failPaymentById(Long paymentId, String reason) {
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado"));

        payment.markAsFailed(reason);
        controlPlanePaymentRepository.save(payment);
    }

    private void validatePayment(Account account, BigDecimal amount, Instant now) {
        if (account == null || account.getId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_AMOUNT, "Valor do pagamento inválido");
        }

        if (account.isDeleted()) {
            throw new ApiException(ApiErrorCode.ACCOUNT_DELETED, "Conta deletada");
        }

        if (account.isBuiltInAccount()) {
            throw new ApiException(ApiErrorCode.BUILTIN_ACCOUNT_NO_BILLING, "Conta BUILTIN não possui billing");
        }

        boolean hasActive = controlPlanePaymentRepository.existsActivePayment(account.getId(), now);
        if (hasActive) {
            throw new ApiException(ApiErrorCode.PAYMENT_ALREADY_EXISTS, "Já existe um pagamento ativo para esta conta");
        }
    }

    // =========================================================
    // Side-effects / mapping
    // =========================================================

    private void sendSuspensionEmail(Account account, String reason) {
        log.info("Enviando email de suspensão para: {} (reason={})", account.getLoginEmail(), reason);
    }

    private void sendPaymentConfirmationEmail(Account account, Payment payment) {
        log.info("Enviando confirmação de pagamento para: {}", account.getLoginEmail());
    }

    private LocalDate calculateNextDueDate(Instant validUntil, Instant now) {
        Instant base = (validUntil != null ? validUntil : now.plusSeconds(30L * 24 * 3600));
        return LocalDate.ofInstant(base, ZoneOffset.UTC);
    }

    private boolean processWithPaymentGateway(Long paymentId, PaymentRequest paymentRequest) {
        log.info("Processando pagamento={} com gateway: {}", paymentId, paymentRequest.paymentGateway());

        try {
            Thread.sleep(300);
            return Math.random() < 0.90;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erro ao processar pagamento no gateway", e);
            return false;
        }
    }

    /**
     * PaymentResponse:
     * (id, accountId, amount, paymentMethod, paymentGateway, paymentStatus, description, paidAt, validUntil, refundedAt, createdAt, updatedAt)
     */
    private PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse(
                payment.getId(),
                payment.getAccount() != null ? payment.getAccount().getId() : null,

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

        if (payment.getStatus() == PaymentStatus.COMPLETED && payment.getAccount() != null) {
            sendPaymentConfirmationEmail(payment.getAccount(), payment);
        }

        return response;
    }
}
