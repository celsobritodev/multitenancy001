package brito.com.multitenancy001.repositories;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.platform.domain.billing.Payment;
import brito.com.multitenancy001.platform.domain.billing.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    List<Payment> findByAccountId(Long accountId);
    
    List<Payment> findByAccountIdAndStatus(Long accountId, PaymentStatus status);
    
    Page<Payment> findByAccountId(Long accountId, Pageable pageable);
    
    Optional<Payment> findByTransactionId(String transactionId);
    
    List<Payment> findByStatus(PaymentStatus status);
    
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime date);
    
    List<Payment> findByValidUntilBeforeAndStatus(LocalDateTime date, PaymentStatus status);
    
    @Query("SELECT p FROM Payment p WHERE p.account.id = :accountId AND p.status = 'COMPLETED' ORDER BY p.paymentDate DESC")
    List<Payment> findCompletedPaymentsByAccount(@Param("accountId") Long accountId);
    
    @Query("SELECT p FROM Payment p WHERE p.account.id = :accountId AND p.validUntil > :now AND p.status = 'COMPLETED'")
    Optional<Payment> findActivePayment(@Param("accountId") Long accountId, @Param("now") LocalDateTime now);
    
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.account.id = :accountId AND p.status = 'COMPLETED' AND p.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalPaidInPeriod(@Param("accountId") Long accountId, 
                                   @Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.account.id = :accountId AND p.status = 'COMPLETED'")
    Long countCompletedPayments(@Param("accountId") Long accountId);
    
    @Query("SELECT p FROM Payment p WHERE p.paymentDate BETWEEN :startDate AND :endDate")
    List<Payment> findPaymentsInPeriod(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT p.account.id, SUM(p.amount) FROM Payment p WHERE p.status = 'COMPLETED' AND p.paymentDate BETWEEN :startDate AND :endDate GROUP BY p.account.id")
    List<Object[]> getRevenueByAccount(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    boolean existsByTransactionId(String transactionId);
}