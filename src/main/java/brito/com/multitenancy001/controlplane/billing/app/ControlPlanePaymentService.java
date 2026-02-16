package brito.com.multitenancy001.controlplane.billing.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.controlplane.accounts.app.AccountStatusService;
import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.controlplane.billing.persistence.ControlPlanePaymentRepository;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
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
import java.util.stream.Collectors;

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
        accountStatusService.changeAccountStatus(
                accountId,
                new AccountStatusChangeCommand(AccountStatus.SUSPENDED)
        );

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findAnyById(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404))
        );

        sendSuspensionEmail(account, reason);
    }

    private void checkExpiredPendingPayments(Instant now) {
        Instant thirtyMinutesAgo = now.minusSeconds(30 * 60);

        publicSchemaUnitOfWork.tx(() -> {
            List<Payment> expiredPayments = controlPlanePaymentRepository
                    .findByStatusAndAudit_CreatedAtBefore(PaymentStatus.PENDING, thirtyMinutesAgo);

            for (Payment payment : expiredPayments) {
                payment.setStatus(PaymentStatus.EXPIRED);
                controlPlanePaymentRepository.save(payment);
            }
        });
    }

    // =========================================================
    // Commands
    // =========================================================

    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {
        if (adminPaymentRequest.accountId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Instant now = appClock.instant();

        Long paymentId = publicSchemaUnitOfWork.tx(() -> {
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

        publicSchemaUnitOfWork.tx(() -> failPaymentById(paymentId, "Falha no processamento do pagamento"));
        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento", 402);
    }

    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        Long accountId = requestIdentity.getCurrentAccountId();
        Instant now = appClock.instant();

        Long paymentId = publicSchemaUnitOfWork.tx(() -> {
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

            return controlPlanePaymentRepository.save(payment).getId();
        });

        boolean ok = processWithPaymentGateway(paymentId, paymentRequest);

        if (ok) {
            Payment payment = publicSchemaUnitOfWork.tx(() -> completePaymentById(paymentId, now));
            return mapToResponse(payment);
        }

        publicSchemaUnitOfWork.tx(() -> failPaymentById(paymentId, "Falha no processamento do pagamento"));
        throw new ApiException(ApiErrorCode.PAYMENT_FAILED, "Falha no processamento do pagamento", 402);
    }

    public PaymentResponse completePaymentManually(Long paymentId) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);
        }

        Instant now = appClock.instant();

        Payment payment = publicSchemaUnitOfWork.tx(() -> {
            Payment p = controlPlanePaymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

            if (p.getStatus() != PaymentStatus.PENDING) {
                throw new ApiException(ApiErrorCode.INVALID_PAYMENT_STATUS, "Pagamento não está pendente", 409);
            }

            return completePaymentById(paymentId, now);
        });

        return mapToResponse(payment);
    }

    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);
        }

        Instant now = appClock.instant();

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

        return mapToResponse(payment);
    }

    // =========================================================
    // Queries
    // =========================================================

    public PaymentResponse getPaymentByIdForMyAccount(Long paymentId) {
        Long accountId = requestIdentity.getCurrentAccountId();

        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findScopedByIdAndAccountId(paymentId, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );

        return mapToResponse(payment);
    }

    public List<PaymentResponse> getPaymentsByMyAccount() {
        Long accountId = requestIdentity.getCurrentAccountId();
        return getPaymentsByAccount(accountId);
    }

    public boolean hasActivePaymentMyAccount() {
        Long accountId = requestIdentity.getCurrentAccountId();
        return hasActivePayment(accountId);
    }

    public List<PaymentResponse> getPaymentsByAccount(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findByAccount_Id(accountId)
                        .stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
        );
    }

    public boolean hasActivePayment(Long accountId) {
        Instant now = appClock.instant();
        return publicSchemaUnitOfWork.readOnly(() -> controlPlanePaymentRepository.existsActivePayment(accountId, now));
    }

    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404))
        );
        return mapToResponse(payment);
    }

    public BigDecimal getTotalRevenue(Instant startDate, Instant endDate) {
        List<Object[]> revenueByAccount = publicSchemaUnitOfWork.readOnly(() ->
                controlPlanePaymentRepository.getRevenueByAccount(startDate, endDate)
        );

        return revenueByAccount.stream()
                .map(obj -> (BigDecimal) obj[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean paymentExistsForAccount(Long paymentId, Long accountId) {
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);
        }
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        return publicSchemaUnitOfWork.readOnly(() -> controlPlanePaymentRepository.existsByIdAndAccount_Id(paymentId, accountId));
    }

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

    public PaymentResponse getPaymentByTransactionId(String transactionId) {
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
        if (transactionId == null || transactionId.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_TRANSACTION_ID, "transactionId é obrigatório", 400);
        }
        return publicSchemaUnitOfWork.readOnly(() -> controlPlanePaymentRepository.existsByTransactionId(transactionId.trim()));
    }

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

    private Payment completePaymentById(Long paymentId, Instant now) {
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
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

        payment.markAsFailed(reason);
        controlPlanePaymentRepository.save(payment);
    }

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

    // =========================================================
    // Side-effects / mapping
    // =========================================================

    private void sendSuspensionEmail(Account account, String reason) {
        log.info("Enviando email de suspensão para: {}", account.getLoginEmail());
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
            Thread.sleep(1000);
            boolean ok = Math.random() < 0.9;

            return ok;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erro ao processar pagamento no gateway", e);
            return false;
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
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
}
