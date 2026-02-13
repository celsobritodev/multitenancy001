package brito.com.multitenancy001.controlplane.billing.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;

@Repository
public interface ControlPlanePaymentRepository extends JpaRepository<Payment, Long> {

    // =========================================================
    // Scoped / account
    // =========================================================

    Optional<Payment> findByIdAndAccount_Id(Long id, Long accountId);

    boolean existsByIdAndAccount_Id(Long id, Long accountId);

    List<Payment> findByAccount_Id(Long accountId);

    List<Payment> findByAccount_IdAndStatus(Long accountId, PaymentStatus status);

    // Usado no PaymentQueryService.listByAccount (ordenado)
    List<Payment> findByAccount_IdOrderByAudit_CreatedAtDesc(Long accountId);

    // =========================================================
    // Generic queries
    // =========================================================

    Optional<Payment> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    // Usado no PaymentQueryService.findByStatus
    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByStatusAndAudit_CreatedAtBefore(PaymentStatus status, Instant date);

    List<Payment> findByValidUntilBeforeAndStatus(Instant date, PaymentStatus status);

    @Query("""
        select p
          from Payment p
         where p.account.id = :accountId
           and p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
         order by p.paymentDate desc
    """)
    List<Payment> findCompletedPaymentsByAccount(@Param("accountId") Long accountId);

    @Query("""
        select p
          from Payment p
         where p.paymentDate between :startDate and :endDate
    """)
    List<Payment> findPaymentsInPeriod(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Pagamento "ativo" = existe COMPLETED com validUntil >= now.
     */
    @Query("""
        select (count(p) > 0)
          from Payment p
         where p.account.id = :accountId
           and p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
           and p.validUntil is not null
           and p.validUntil >= :now
    """)
    boolean existsActivePayment(@Param("accountId") Long accountId, @Param("now") Instant now);

    /**
     * Total pago no período para uma conta (somente COMPLETED).
     * Usado em PaymentQueryService.getTotalPaidInPeriod(accountId, start, end)
     */
    @Query("""
        select coalesce(sum(p.amount), 0)
          from Payment p
         where p.account.id = :accountId
           and p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
           and p.paymentDate between :startDate and :endDate
    """)
    BigDecimal sumTotalPaidInPeriod(
            @Param("accountId") Long accountId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Contagem de pagamentos COMPLETED para uma conta.
     */
    @Query("""
        select count(p)
          from Payment p
         where p.account.id = :accountId
           and p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
    """)
    long countCompletedPayments(@Param("accountId") Long accountId);

    /**
     * Receita global no período (somente COMPLETED).
     * Usado no ControlPlanePaymentService.getTotalRevenue
     */
    @Query("""
        select coalesce(sum(p.amount), 0)
          from Payment p
         where p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
           and p.paymentDate between :startDate and :endDate
    """)
    BigDecimal sumRevenueInPeriod(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
