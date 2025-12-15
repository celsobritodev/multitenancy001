package brito.com.multitenancy001.services;

import brito.com.multitenancy001.dtos.PaymentRequest;
import brito.com.multitenancy001.dtos.PaymentResponse;
import brito.com.multitenancy001.entities.master.Account;
import brito.com.multitenancy001.entities.master.AccountStatus;
import brito.com.multitenancy001.entities.master.Payment;
import brito.com.multitenancy001.entities.master.PaymentStatus;
import brito.com.multitenancy001.exceptions.ApiException;
import brito.com.multitenancy001.repositories.AccountRepository;
import brito.com.multitenancy001.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    // private final EmailService emailService; // Opcional

    @Scheduled(cron = "${app.payment.check-cron:0 0 0 * * *}")
    public void checkPayments() {
        log.info("Iniciando verificação de pagamentos...");
        LocalDateTime today = LocalDateTime.now();

        List<Account> expiredTrials = accountRepository
                .findByStatus(AccountStatus.FREE_TRIAL)
                .stream()
                .filter(account -> account.getTrialEndDate() != null &&
                        account.getTrialEndDate().isBefore(today))
                .collect(Collectors.toList());

        for (Account account : expiredTrials) {
            suspendAccount(account, "Trial expirado");
        }

        List<Account> overdueAccounts = accountRepository
                .findByPaymentDueDateBefore(today);

        for (Account account : overdueAccounts) {
            if (account.getStatus() == AccountStatus.ACTIVE) {
                suspendAccount(account, "Pagamento atrasado");
            }
        }

        checkExpiredPendingPayments();
    }

    private void suspendAccount(Account account, String reason) {
        account.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(account);
        sendSuspensionEmail(account, reason);
    }

    private void checkExpiredPendingPayments() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

        List<Payment> expiredPayments = paymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, thirtyMinutesAgo);

        for (Payment payment : expiredPayments) {
            payment.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
        }
    }

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ApiException(
                        "ACCOUNT_NOT_FOUND",
                        "Conta não encontrada",
                        404
                ));

        validatePayment(account, request);

        Payment payment = Payment.builder()
                .account(account)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .paymentGateway(request.paymentGateway())
                .description(request.description())
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);

        boolean paymentSuccessful = processWithPaymentGateway(payment, request);

        if (paymentSuccessful) {
            completePayment(payment, account);
            return mapToResponse(payment, "Pagamento processado com sucesso");
        }

        failPayment(payment, "Falha no processamento do pagamento");

        throw new ApiException(
                "PAYMENT_FAILED",
                "Falha no processamento do pagamento",
                402
        );
    }

    @Transactional
    public PaymentResponse completePaymentManually(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(
                        "PAYMENT_NOT_FOUND",
                        "Pagamento não encontrado",
                        404
                ));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(
                    "INVALID_PAYMENT_STATUS",
                    "Pagamento não está pendente",
                    409
            );
        }

        completePayment(payment, payment.getAccount());
        return mapToResponse(payment, "Pagamento concluído manualmente");
    }

    private void completePayment(Payment payment, Account account) {
        payment.markAsCompleted();
        payment.setTransactionId(generateTransactionId());
        paymentRepository.save(payment);

        account.setStatus(AccountStatus.ACTIVE);
        account.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil()));
        accountRepository.save(account);

        sendPaymentConfirmationEmail(account, payment);
    }

    private void failPayment(Payment payment, String reason) {
        payment.markAsFailed(reason);
        paymentRepository.save(payment);
    }

    private void validatePayment(Account account, PaymentRequest request) {

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    "INVALID_AMOUNT",
                    "Valor do pagamento inválido",
                    400
            );
        }

        if (account.isDeleted()) {
            throw new ApiException(
                    "ACCOUNT_DELETED",
                    "Conta deletada",
                    410
            );
        }

        Optional<Payment> activePayment =
                paymentRepository.findActivePayment(account.getId(), LocalDateTime.now());

        if (activePayment.isPresent()) {
            throw new ApiException(
                    "PAYMENT_ALREADY_EXISTS",
                    "Já existe um pagamento ativo para esta conta",
                    409
            );
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByAccount(Long accountId) {
        return paymentRepository.findByAccountId(accountId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(
                        "PAYMENT_NOT_FOUND",
                        "Pagamento não encontrado",
                        404
                ));

        return mapToResponse(payment);
    }

    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        return paymentRepository
                .findActivePayment(accountId, LocalDateTime.now())
                .isPresent();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> revenueByAccount =
                paymentRepository.getRevenueByAccount(startDate, endDate);

        return revenueByAccount.stream()
                .map(obj -> (BigDecimal) obj[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(
                        "PAYMENT_NOT_FOUND",
                        "Pagamento não encontrado",
                        404
                ));

        if (!payment.canBeRefunded()) {
            throw new ApiException(
                    "PAYMENT_NOT_REFUNDABLE",
                    "Pagamento não pode ser reembolsado",
                    409
            );
        }

        if (amount == null) {
            payment.refundFully(reason);
        } else {
            payment.refundPartially(amount, reason);
        }

        paymentRepository.save(payment);
        return mapToResponse(payment, "Reembolso processado");
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

    private PaymentResponse mapToResponse(Payment payment, String message) {
        return mapToResponse(payment);
    }

    private void sendSuspensionEmail(Account account, String reason) {
        log.info("Enviando email de suspensão para: {}", account.getCompanyEmail());
    }

    private void sendPaymentConfirmationEmail(Account account, Payment payment) {
        log.info("Enviando confirmação de pagamento para: {}", account.getCompanyEmail());
    }

    private String generateTransactionId() {
        return "TXN_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private LocalDateTime calculateNextDueDate(LocalDateTime validUntil) {
        return validUntil != null
                ? validUntil
                : LocalDateTime.now().plusMonths(1);
    }
    
    
    private boolean processWithPaymentGateway(Payment payment, PaymentRequest request) {

        log.info("Processando pagamento com gateway: {}", request.paymentGateway());

        try {
            // Simulação de comunicação com gateway externo
            // (Stripe, Mercado Pago, PagSeguro, etc.)
            Thread.sleep(1000);

            // Simulação: 90% de chance de sucesso
            return Math.random() < 0.9;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erro ao processar pagamento no gateway", e);
            return false;
        }
    }
 
    
    
    
    
}
