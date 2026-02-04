package brito.com.multitenancy001.controlplane.billing.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ControlPlanePaymentRepository extends JpaRepository<Payment, Long> {

    // =========================================================
    // API "DDD": accountId (o domínio trabalha com ids)
    // Internamente (JPA): account.id (property-path)
    // =========================================================

    // ---------- scoped ----------
    default Optional<Payment> findScopedByIdAndAccountId(Long id, Long accountId) {
        return findByIdAndAccount_Id(id, accountId);
    }

    default Optional<Payment> findByIdAndAccountId(Long id, Long accountId) {
        return findByIdAndAccount_Id(id, accountId);
    }

    default boolean existsByIdAndAccountId(Long id, Long accountId) {
        return existsByIdAndAccount_Id(id, accountId);
    }

    // ---------- queries por account ----------
    default List<Payment> findByAccountId(Long accountId) {
        return findByAccount_Id(accountId);
    }

    default Page<Payment> findByAccountId(Long accountId, Pageable pageable) {
        return findByAccount_Id(accountId, pageable);
    }

    default List<Payment> findByAccountIdAndStatus(Long accountId, PaymentStatus status) {
        return findByAccount_IdAndStatus(accountId, status);
    }

    default List<Payment> findByAccountIdOrderByCreatedAtDesc(Long accountId) {
        // compat: se alguém ainda chamar esse nome antigo
        return findByAccount_IdOrderByAudit_CreatedAtDesc(accountId);
    }

    default List<Payment> findByAccountIdOrderByAudit_CreatedAtDesc(Long accountId) {
        return findByAccount_IdOrderByAudit_CreatedAtDesc(accountId);
    }

    // =========================================================
    // Métodos reais (JPA property-path) - NÃO use diretamente nos services
    // =========================================================

    Optional<Payment> findByIdAndAccount_Id(Long id, Long accountId);
    boolean existsByIdAndAccount_Id(Long id, Long accountId);

    List<Payment> findByAccount_Id(Long accountId);
    Page<Payment> findByAccount_Id(Long accountId, Pageable pageable);
    List<Payment> findByAccount_IdAndStatus(Long accountId, PaymentStatus status);

    List<Payment> findByAccount_IdOrderByAudit_CreatedAtDesc(Long accountId);

    // =========================================================
    // Outras queries
    // =========================================================

    Optional<Payment> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);

    List<Payment> findByStatus(PaymentStatus status);

    // ✅ audit.createdAt (clean, sem JPQL)
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
        select coalesce(sum(p.amount), 0)
          from Payment p
         where p.account.id = :accountId
           and p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
           and p.paymentDate between :startDate and :endDate
    """)
    BigDecimal getTotalPaidInPeriod(
            @Param("accountId") Long accountId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
        select count(p)
          from Payment p
         where p.account.id = :accountId
           and p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
    """)
    Long countCompletedPayments(@Param("accountId") Long accountId);

    @Query("""
        select p
          from Payment p
         where p.paymentDate between :startDate and :endDate
    """)
    List<Payment> findPaymentsInPeriod(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
        select p.account.id, sum(p.amount)
          from Payment p
         where p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
           and p.paymentDate between :startDate and :endDate
         group by p.account.id
    """)
    List<Object[]> getRevenueByAccount(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
        select (count(p) > 0)
          from Payment p
         where p.account.id = :accountId
           and p.status = brito.com.multitenancy001.shared.domain.billing.PaymentStatus.COMPLETED
           and p.validUntil is not null
           and p.validUntil >= :now
    """)
    boolean existsActivePayment(@Param("accountId") Long accountId, @Param("now") Instant now);
}
