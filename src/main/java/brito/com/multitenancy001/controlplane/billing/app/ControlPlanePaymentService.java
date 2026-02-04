package brito.com.multitenancy001.controlplane.billing.app;

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
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final AccountRepository accountRepository;
    private final ControlPlanePaymentRepository controlPlanePaymentRepository;
    private final SecurityUtils securityUtils;
    private final AppClock appClock;
    private final AccountStatusService accountStatusService;

    @Scheduled(cron = "${app.payment.check-cron:0 0 0 * * *}")
    public void checkPayments() {
        log.info("Iniciando verificação de pagamentos...");
        Instant now = appClock.instant();

        // Trials expirados: trialEndAt é Instant (timestamptz)
        List<Account> expiredTrials = accountRepository.findExpiredTrialsNotDeleted(now, AccountStatus.FREE_TRIAL);

        for (Account account : expiredTrials) {
            if (account.getStatus() != AccountStatus.SUSPENDED) {
                suspendAccount(account, "Trial expirado");
            }
        }

        // Pagamentos vencidos: paymentDueDate é LocalDate (DATE) => hoje é LocalDate em UTC (sem timezone implícito)
        LocalDate todayUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<Account> overdueAccounts = accountRepository.findOverdueAccountsNotDeleted(AccountStatus.ACTIVE, todayUtc);

        for (Account account : overdueAccounts) {
            if (account.getStatus() != AccountStatus.SUSPENDED) {
                suspendAccount(account, "Pagamento atrasado");
            }
        }

        checkExpiredPendingPayments(now);
    }

    private void suspendAccount(Account account, String reason) {
        accountStatusService.changeAccountStatus(
                account.getId(),
                new AccountStatusChangeCommand(AccountStatus.SUSPENDED)
        );

        sendSuspensionEmail(account, reason);
    }

    private void checkExpiredPendingPayments(Instant now) {
        Instant thirtyMinutesAgo = now.minusSeconds(30 * 60);

        List<Payment> expiredPayments = controlPlanePaymentRepository
                .findByStatusAndAudit_CreatedAtBefore(PaymentStatus.PENDING, thirtyMinutesAgo);

        for (Payment payment : expiredPayments) {
            payment.setStatus(PaymentStatus.EXPIRED);
            controlPlanePaymentRepository.save(payment);
        }
    }

    @Transactional
    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {

        if (adminPaymentRequest.accountId() == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        }

        Account account = accountRepository.findById(adminPaymentRequest.accountId())
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        Instant now = appClock.instant();
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

        payment = controlPlanePaymentRepository.save(payment);

        boolean ok = processWithPaymentGateway(payment,
                new PaymentRequest(
                        adminPaymentRequest.amount(),
                        adminPaymentRequest.paymentMethod(),
                        adminPaymentRequest.paymentGateway(),
                        adminPaymentRequest.description()
                )
        );

        if (ok) {
            completePayment(payment, account, now);
            return mapToResponse(payment);
        }

        failPayment(payment, "Falha no processamento do pagamento");
        throw new ApiException("PAYMENT_FAILED", "Falha no processamento do pagamento", 402);
    }

    @Transactional
    public PaymentResponse processPaymentForMyAccount(PaymentRequest paymentRequest) {
        Long accountId = securityUtils.getCurrentAccountId();
        Account account = findAccountOrThrow(accountId);

        Instant now = appClock.instant();
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

        payment = controlPlanePaymentRepository.save(payment);

        boolean ok = processWithPaymentGateway(payment, paymentRequest);

        if (ok) {
            completePayment(payment, account, now);
            return mapToResponse(payment);
        }

        failPayment(payment, "Falha no processamento do pagamento");
        throw new ApiException("PAYMENT_FAILED", "Falha no processamento do pagamento", 402);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByIdForMyAccount(Long paymentId) {
        Long accountId = securityUtils.getCurrentAccountId();

        Payment payment = controlPlanePaymentRepository.findScopedByIdAndAccountId(paymentId, accountId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByMyAccount() {
        Long accountId = securityUtils.getCurrentAccountId();
        return getPaymentsByAccount(accountId);
    }

    @Transactional(readOnly = true)
    public boolean hasActivePaymentMyAccount() {
        Long accountId = securityUtils.getCurrentAccountId();
        return hasActivePayment(accountId);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByAccount(Long accountId) {
        return controlPlanePaymentRepository.findByAccount_Id(accountId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        return controlPlanePaymentRepository.existsActivePayment(accountId, appClock.instant());
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse completePaymentManually(Long paymentId) {

        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException("INVALID_PAYMENT_STATUS", "Pagamento não está pendente", 409);
        }

        Instant now = appClock.instant();
        completePayment(payment, payment.getAccount(), now);
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {

        Payment payment = controlPlanePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

        Instant now = appClock.instant();

        if (!payment.canBeRefunded(now)) {
            throw new ApiException("PAYMENT_NOT_REFUNDABLE", "Pagamento não pode ser reembolsado", 409);
        }

        if (amount == null) {
            payment.refundFully(now, reason);
        } else {
            payment.refundPartially(now, amount, reason);
        }

        controlPlanePaymentRepository.save(payment);
        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue(Instant startDate, Instant endDate) {
        List<Object[]> revenueByAccount = controlPlanePaymentRepository.getRevenueByAccount(startDate, endDate);

        return revenueByAccount.stream()
                .map(obj -> (BigDecimal) obj[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Account findAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

    private void completePayment(Payment payment, Account account, Instant now) {
        payment.markAsCompleted(now);
        controlPlanePaymentRepository.save(payment);

        account.setStatus(AccountStatus.ACTIVE);

        // ✅ paymentDueDate é LocalDate (civil)
        account.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil(), now));
        accountRepository.save(account);

        sendPaymentConfirmationEmail(account, payment);
    }

    private void failPayment(Payment payment, String reason) {
        payment.markAsFailed(reason);
        controlPlanePaymentRepository.save(payment);
    }

    private void validatePayment(Account account, BigDecimal amount, Instant now) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("INVALID_AMOUNT", "Valor do pagamento inválido", 400);
        }

        if (account.isDeleted()) {
            throw new ApiException("ACCOUNT_DELETED", "Conta deletada", 410);
        }

        if (account.isBuiltInAccount()) {
            throw new ApiException("BUILTIN_ACCOUNT_NO_BILLING", "Conta BUILTIN não possui billing", 409);
        }

        boolean hasActive = controlPlanePaymentRepository.existsActivePayment(account.getId(), now);

        if (hasActive) {
            throw new ApiException("PAYMENT_ALREADY_EXISTS", "Já existe um pagamento ativo para esta conta", 409);
        }
    }

    @Transactional(readOnly = true)
    public boolean paymentExistsForAccount(Long paymentId, Long accountId) {
        if (paymentId == null) {
            throw new ApiException("PAYMENT_ID_REQUIRED", "paymentId é obrigatório", 400);
        }
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        }
        return controlPlanePaymentRepository.existsByIdAndAccount_Id(paymentId, accountId);
    }


    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByAccountAndStatus(Long accountId, PaymentStatus status) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        }
        if (status == null) {
            throw new ApiException("INVALID_STATUS", "status é obrigatório", 400);
        }

        return controlPlanePaymentRepository.findByAccount_IdAndStatus(accountId, status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new ApiException("INVALID_TRANSACTION_ID", "transactionId é obrigatório", 400);
        }

        Payment payment = controlPlanePaymentRepository.findByTransactionId(transactionId.trim())
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public boolean existsByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new ApiException("INVALID_TRANSACTION_ID", "transactionId é obrigatório", 400);
        }
        return controlPlanePaymentRepository.existsByTransactionId(transactionId.trim());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByValidUntilBeforeAndStatus(Instant date, PaymentStatus status) {
        if (date == null) {
            throw new ApiException("INVALID_DATE", "date é obrigatório", 400);
        }
        if (status == null) {
            throw new ApiException("INVALID_STATUS", "status é obrigatório", 400);
        }

        return controlPlanePaymentRepository.findByValidUntilBeforeAndStatus(date, status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getCompletedPaymentsByAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        }

        return controlPlanePaymentRepository.findCompletedPaymentsByAccount(accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsInPeriod(Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null) {
            throw new ApiException("INVALID_DATE_RANGE", "startDate e endDate são obrigatórios", 400);
        }
        if (endDate.isBefore(startDate)) {
            throw new ApiException("INVALID_DATE_RANGE", "endDate deve ser >= startDate", 400);
        }

        return controlPlanePaymentRepository.findPaymentsInPeriod(startDate, endDate)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

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

    private boolean processWithPaymentGateway(Payment payment, PaymentRequest paymentRequest) {

        log.info("Processando pagamento com gateway: {}", paymentRequest.paymentGateway());

        try {
            Thread.sleep(1000);
            return Math.random() < 0.9;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erro ao processar pagamento no gateway", e);
            return false;
        }
    }

    /**
     * ✅ PaymentResponse record (ordem e semântica):
     * (id, accountId, amount, paymentMethod, paymentGateway, paymentStatus, description, paidAt, validUntil, refundedAt, createdAt, updatedAt)
     */
    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAccount().getId(),

                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getStatus(),

                payment.getDescription(),

                // paidAt: no seu domínio é paymentDate
                payment.getPaymentDate(),
                payment.getValidUntil(),
                payment.getRefundedAt(),

                // auditoria única
                payment.getAudit() != null ? payment.getAudit().getCreatedAt() : null,
                payment.getAudit() != null ? payment.getAudit().getUpdatedAt() : null
        );
    }
}
