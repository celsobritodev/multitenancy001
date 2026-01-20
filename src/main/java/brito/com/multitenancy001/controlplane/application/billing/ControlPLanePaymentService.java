package brito.com.multitenancy001.controlplane.application.billing;

import brito.com.multitenancy001.controlplane.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.controlplane.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.controlplane.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.domain.billing.Payment;
import brito.com.multitenancy001.controlplane.domain.billing.PaymentStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.billing.ControlPlanePaymentRepository;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlanePaymentService {

    private final AccountRepository accountRepository;
    private final ControlPlanePaymentRepository paymentRepository;
    private final SecurityUtils securityUtils;
    private final AppClock appClock;
    
    
@Scheduled(cron = "${app.payment.check-cron:0 0 0 * * *}")
public void checkPayments() {
    log.info("Iniciando verificação de pagamentos...");
    LocalDateTime now = appClock.now();

    // Trials expirados
    List<Account> expiredTrials =
            accountRepository.findExpiredTrials(now, AccountStatus.FREE_TRIAL);

    for (Account account : expiredTrials) {
        if (account.getStatus() != AccountStatus.SUSPENDED) {
            suspendAccount(account, "Trial expirado");
        }
    }

    // Pagamentos vencidos
    List<Account> overdueAccounts =
            accountRepository.findOverdueAccounts(AccountStatus.ACTIVE, now);

    for (Account account : overdueAccounts) {
        if (account.getStatus() != AccountStatus.SUSPENDED) {
            suspendAccount(account, "Pagamento atrasado");
        }
    }

    checkExpiredPendingPayments(now);
}

    
    
    

    private void suspendAccount(Account account, String reason) {
        account.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(account);
        sendSuspensionEmail(account, reason);
    }

    private void checkExpiredPendingPayments(LocalDateTime now) {
        LocalDateTime thirtyMinutesAgo = now.minusMinutes(30);

        List<Payment> expiredPayments = paymentRepository.findByStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, thirtyMinutesAgo
        );

        for (Payment payment : expiredPayments) {
            payment.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
        }
    }

    @Transactional
    public PaymentResponse processPaymentForAccount(AdminPaymentRequest adminPaymentRequest) {

        if (adminPaymentRequest.accountId() == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
        }

        Account account = accountRepository.findById(adminPaymentRequest.accountId())
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

        LocalDateTime now = appClock.now();
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

        payment = paymentRepository.save(payment);

        boolean ok = processWithPaymentGateway(
                payment,
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

        LocalDateTime now = appClock.now();
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

        payment = paymentRepository.save(payment);

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

        Payment payment = paymentRepository.findByIdAndAccountId(paymentId, accountId)
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
        return paymentRepository.findByAccountId(accountId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        return paymentRepository.findActivePayment(accountId, appClock.now()).isPresent();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse completePaymentManually(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException("INVALID_PAYMENT_STATUS", "Pagamento não está pendente", 409);
        }

        LocalDateTime now = appClock.now();
        completePayment(payment, payment.getAccount(), now);
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

        LocalDateTime now = appClock.now();

        if (!payment.canBeRefunded(now)) {
            throw new ApiException("PAYMENT_NOT_REFUNDABLE", "Pagamento não pode ser reembolsado", 409);
        }

        if (amount == null) {
            payment.refundFully(now, reason);
        } else {
            payment.refundPartially(now, amount, reason);
        }

        paymentRepository.save(payment);
        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> revenueByAccount = paymentRepository.getRevenueByAccount(startDate, endDate);

        return revenueByAccount.stream()
                .map(obj -> (BigDecimal) obj[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /*
     * ========================================================= PRIVATE HELPERS
     * =========================================================
     */

    private Account findAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
    }

    private void completePayment(Payment payment, Account account, LocalDateTime now) {
        payment.markAsCompleted(now);
        paymentRepository.save(payment);

        account.setStatus(AccountStatus.ACTIVE);
        account.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil(), now));
        accountRepository.save(account);

        sendPaymentConfirmationEmail(account, payment);
    }

    private void failPayment(Payment payment, String reason) {
        payment.markAsFailed(reason);
        paymentRepository.save(payment);
    }

    private void validatePayment(Account account, BigDecimal amount, LocalDateTime now) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException("INVALID_AMOUNT", "Valor do pagamento inválido", 400);
        }

        if (account.isDeleted()) {
            throw new ApiException("ACCOUNT_DELETED", "Conta deletada", 410);
        }
        
        if (account.isBuiltInAccount()) {
            throw new ApiException("BUILTIN ACCOUNT_NO_BILLING", "Conta BUILTIN não possui billing", 409);
        }
 

        Optional<Payment> activePayment = paymentRepository.findActivePayment(account.getId(), now);

        if (activePayment.isPresent()) {
            throw new ApiException("PAYMENT_ALREADY_EXISTS", "Já existe um pagamento ativo para esta conta", 409);
        }
    }
    
    
 // =========================================================
 // ✅ QUERIES / HELPERS para usar métodos do PaymentRepository
 // =========================================================

 @Transactional(readOnly = true)
 public boolean paymentExistsForAccount(Long paymentId, Long accountId) {
     if (paymentId == null) {
         throw new ApiException("PAYMENT_ID_REQUIRED", "paymentId é obrigatório", 400);
     }
     if (accountId == null) {
         throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
     }
     return paymentRepository.existsByIdAndAccountId(paymentId, accountId);
 }

 @Transactional(readOnly = true)
 public List<PaymentResponse> getPaymentsByAccountAndStatus(Long accountId, PaymentStatus status) {
     if (accountId == null) {
         throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
     }
     if (status == null) {
         throw new ApiException("INVALID_STATUS", "status é obrigatório", 400);
     }

     return paymentRepository.findByAccountIdAndStatus(accountId, status).stream()
             .map(this::mapToResponse)
             .toList();
 }

 @Transactional(readOnly = true)
 public PaymentResponse getPaymentByTransactionId(String transactionId) {
     if (transactionId == null || transactionId.isBlank()) {
         throw new ApiException("INVALID_TRANSACTION_ID", "transactionId é obrigatório", 400);
     }

     Payment payment = paymentRepository.findByTransactionId(transactionId.trim())
             .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

     return mapToResponse(payment);
 }

 @Transactional(readOnly = true)
 public boolean existsByTransactionId(String transactionId) {
     if (transactionId == null || transactionId.isBlank()) {
         throw new ApiException("INVALID_TRANSACTION_ID", "transactionId é obrigatório", 400);
     }
     return paymentRepository.existsByTransactionId(transactionId.trim());
 }

 @Transactional(readOnly = true)
 public List<PaymentResponse> getPaymentsByValidUntilBeforeAndStatus(LocalDateTime date, PaymentStatus status) {
     if (date == null) {
         throw new ApiException("INVALID_DATE", "date é obrigatório", 400);
     }
     if (status == null) {
         throw new ApiException("INVALID_STATUS", "status é obrigatório", 400);
     }

     return paymentRepository.findByValidUntilBeforeAndStatus(date, status).stream()
             .map(this::mapToResponse)
             .toList();
 }

 @Transactional(readOnly = true)
 public List<PaymentResponse> getCompletedPaymentsByAccount(Long accountId) {
     if (accountId == null) {
         throw new ApiException("ACCOUNT_REQUIRED", "accountId é obrigatório", 400);
     }

     return paymentRepository.findCompletedPaymentsByAccount(accountId).stream()
             .map(this::mapToResponse)
             .toList();
 }

 @Transactional(readOnly = true)
 public List<PaymentResponse> getPaymentsInPeriod(LocalDateTime startDate, LocalDateTime endDate) {
     if (startDate == null || endDate == null) {
         throw new ApiException("INVALID_DATE_RANGE", "startDate e endDate são obrigatórios", 400);
     }
     if (endDate.isBefore(startDate)) {
         throw new ApiException("INVALID_DATE_RANGE", "endDate deve ser >= startDate", 400);
     }

     return paymentRepository.findPaymentsInPeriod(startDate, endDate).stream()
             .map(this::mapToResponse)
             .toList();
 }


   

    private void sendSuspensionEmail(Account account, String reason) {
        log.info("Enviando email de suspensão para: {}", account.getLoginEmail());
    }

    private void sendPaymentConfirmationEmail(Account account, Payment payment) {
        log.info("Enviando confirmação de pagamento para: {}", account.getLoginEmail());
    }

    private LocalDateTime calculateNextDueDate(LocalDateTime validUntil, LocalDateTime now) {
        return validUntil != null ? validUntil : now.plusMonths(1);
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
    
    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAccount().getId(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getValidUntil(),
                payment.getStatus().name(),
                payment.getTransactionId(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
