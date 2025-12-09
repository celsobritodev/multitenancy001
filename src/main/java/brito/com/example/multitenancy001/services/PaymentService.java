package brito.com.example.multitenancy001.services;




import brito.com.example.multitenancy001.dtos.PaymentRequest;
import brito.com.example.multitenancy001.dtos.PaymentResponse;
import brito.com.example.multitenancy001.entities.master.Account;
import brito.com.example.multitenancy001.entities.master.AccountStatus;
import brito.com.example.multitenancy001.entities.master.Payment;
import brito.com.example.multitenancy001.entities.master.PaymentStatus;
import brito.com.example.multitenancy001.repositories.AccountRepository;
import brito.com.example.multitenancy001.repositories.PaymentRepository;
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
    // para implementar
   // private final EmailService emailService; // Opcional
    
    @Scheduled(cron = "${app.payment.check-cron:0 0 0 * * *}")
    public void checkPayments() {
        log.info("Iniciando verificação de pagamentos...");
        LocalDateTime today = LocalDateTime.now();
        
        // Contas com trial expirado
        List<Account> expiredTrials = accountRepository
            .findByStatus(AccountStatus.FREE_TRIAL)
            .stream()
            .filter(account -> account.getTrialEndDate() != null && 
                    account.getTrialEndDate().isBefore(today))
            .collect(Collectors.toList());
        
        log.info("Encontradas {} contas com trial expirado", expiredTrials.size());
        for (Account account : expiredTrials) {
            suspendAccount(account, "Trial expirado");
        }
        
        // Contas com pagamento atrasado
        List<Account> overdueAccounts = accountRepository
            .findByPaymentDueDateBefore(today);
        
        log.info("Encontradas {} contas com pagamento atrasado", overdueAccounts.size());
        for (Account account : overdueAccounts) {
            if (account.getStatus() == AccountStatus.ACTIVE) {
                suspendAccount(account, "Pagamento atrasado");
            }
        }
        
        // Verificar pagamentos pendentes expirados
        checkExpiredPendingPayments();
        
        log.info("Verificação de pagamentos concluída");
    }
    
    private void suspendAccount(Account account, String reason) {
        log.info("Suspender conta {}: {}", account.getId(), reason);
        account.setStatus(AccountStatus.SUSPENDED);
        accountRepository.save(account);
        
        // Enviar notificação por email
        sendSuspensionEmail(account, reason);
    }
    
    private void checkExpiredPendingPayments() {
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);
        List<Payment> expiredPayments = paymentRepository
            .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, thirtyMinutesAgo);
        
        if (!expiredPayments.isEmpty()) {
            log.info("Encontrados {} pagamentos pendentes expirados", expiredPayments.size());
            
            for (Payment payment : expiredPayments) {
                payment.setStatus(PaymentStatus.EXPIRED);
                paymentRepository.save(payment);
                log.info("Pagamento {} marcado como expirado", payment.getId());
            }
        }
    }
    
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processando pagamento para conta {}: {}", request.accountId(), request.amount());
        
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        
        // Validações
        validatePayment(account, request);
        
        // Criar registro de pagamento
        Payment payment = Payment.builder()
                .account(account)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .paymentGateway(request.paymentGateway())
                .description(request.description())
                .status(PaymentStatus.PENDING)
                .build();
        
        payment = paymentRepository.save(payment);
        log.info("Pagamento criado com ID: {}", payment.getId());
        
        // Processar pagamento com gateway (simulação)
        boolean paymentSuccessful = processWithPaymentGateway(payment, request);
        
        if (paymentSuccessful) {
            completePayment(payment, account);
            return mapToResponse(payment, "Pagamento processado com sucesso");
        } else {
            failPayment(payment, "Falha no processamento do pagamento");
            throw new RuntimeException("Falha no processamento do pagamento");
        }
    }
    
    @Transactional
    public PaymentResponse completePaymentManually(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
        
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Pagamento não está pendente");
        }
        
        Account account = payment.getAccount();
        completePayment(payment, account);
        
        return mapToResponse(payment, "Pagamento concluído manualmente");
    }
    
    private void completePayment(Payment payment, Account account) {
        // Atualizar status do pagamento
        payment.markAsCompleted();
        payment.setTransactionId(generateTransactionId());
        payment = paymentRepository.save(payment);
        
        // Atualizar status da conta
        account.setStatus(AccountStatus.ACTIVE);
        account.setPaymentDueDate(calculateNextDueDate(payment.getValidUntil()));
        accountRepository.save(account);
        
        log.info("Pagamento {} concluído para conta {}", payment.getId(), account.getId());
        
        // Enviar confirmação
        sendPaymentConfirmationEmail(account, payment);
    }
    
    private void failPayment(Payment payment, String reason) {
        payment.markAsFailed(reason);
        paymentRepository.save(payment);
        log.error("Pagamento {} falhou: {}", payment.getId(), reason);
    }
    
    private void validatePayment(Account account, PaymentRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Valor do pagamento inválido");
        }
        
        if (account.isDeleted()) {
            throw new RuntimeException("Conta deletada");
        }
        
        // Verificar se já existe um pagamento ativo recente
        Optional<Payment> activePayment = paymentRepository.findActivePayment(
                account.getId(), LocalDateTime.now());
        
        if (activePayment.isPresent()) {
            throw new RuntimeException("Já existe um pagamento ativo para esta conta");
        }
    }
    
    private boolean processWithPaymentGateway(Payment payment, PaymentRequest request) {
        // Simulação de processamento com gateway de pagamento
        // Em produção, integrar com PayPal, Stripe, PagSeguro, etc.
        log.info("Processando pagamento com gateway: {}", request.paymentGateway());
        
        try {
            // Simular delay de processamento
            Thread.sleep(1000);
            
            // Simulação: 90% de chance de sucesso
            return Math.random() < 0.9;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    private String generateTransactionId() {
        return "TXN_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private LocalDateTime calculateNextDueDate(LocalDateTime validUntil) {
        if (validUntil == null) {
            return LocalDateTime.now().plusMonths(1);
        }
        return validUntil;
    }
    
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByAccount(Long accountId) {
        return paymentRepository.findByAccountId(accountId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
        return mapToResponse(payment);
    }
    
    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        Optional<Payment> activePayment = paymentRepository.findActivePayment(
                accountId, LocalDateTime.now());
        return activePayment.isPresent();
    }
    
    @Transactional(readOnly = true)
    public BigDecimal getTotalRevenue(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> revenueByAccount = paymentRepository.getRevenueByAccount(startDate, endDate);
        return revenueByAccount.stream()
                .map(obj -> (BigDecimal) obj[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    @Transactional
    public PaymentResponse refundPayment(Long paymentId, BigDecimal amount, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado"));
        
        if (!payment.canBeRefunded()) {
            throw new RuntimeException("Pagamento não pode ser reembolsado");
        }
        
        if (amount == null) {
            // Reembolso total
            payment.refundFully(reason);
        } else {
            // Reembolso parcial
            payment.refundPartially(amount, reason);
        }
        
        payment = paymentRepository.save(payment);
        log.info("Pagamento {} reembolsado. Valor: {}", paymentId, 
                payment.getRefundAmount() != null ? payment.getRefundAmount() : payment.getAmount());
        
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
        PaymentResponse response = mapToResponse(payment);
        // Se quiser incluir mensagem, pode criar um record diferente
        return response;
    }
    
    private void sendSuspensionEmail(Account account, String reason) {
        // Implementar envio de email
        log.info("Enviando email de suspensão para: {}", account.getCompanyEmail());
        // emailService.sendSuspensionEmail(account, reason);
    }
    
    private void sendPaymentConfirmationEmail(Account account, Payment payment) {
        // Implementar envio de email
        log.info("Enviando confirmação de pagamento para: {}", account.getCompanyEmail());
        // emailService.sendPaymentConfirmation(account, payment);
    }
}