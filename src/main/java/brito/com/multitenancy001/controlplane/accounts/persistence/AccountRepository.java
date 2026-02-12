package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountType;
import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByTaxCountryCodeAndTaxIdTypeAndTaxIdNumberAndDeletedFalse(
            String taxCountryCode, TaxIdType taxIdType, String taxIdNumber
    );

    List<Account> findAllByDeletedFalse();

    Optional<Account> findBySlugAndDeletedFalseIgnoreCase(String slug);

    Optional<AccountResolverProjection> findProjectionByIdAndDeletedFalse(Long id);

    Optional<AccountResolverProjection> findProjectionBySlugAndDeletedFalseIgnoreCase(String slug);

    Optional<Account> findByIdAndDeletedFalse(Long id);

    @Query("""
            SELECT a
              FROM Account a
             WHERE a.id = :id
               AND a.deleted = false
               AND a.status IN ('ACTIVE', 'FREE_TRIAL')
           """)
    Optional<Account> findEnabledById(@Param("id") Long id);

    Optional<Account> findAnyById(Long id);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.deleted = false AND a.status IN :statuses")
    long countByStatusesAndDeletedFalse(@Param("statuses") List<AccountStatus> statuses);

    default long countOperationalAccounts() {
        return countByStatusesAndDeletedFalse(List.of(AccountStatus.ACTIVE, AccountStatus.FREE_TRIAL));
    }

    Page<Account> findByStatusAndDeletedFalse(AccountStatus status, Pageable pageable);

    @Query("""
            SELECT a
              FROM Account a
             WHERE a.deleted = false
               AND a.audit.createdAt BETWEEN :start AND :end
           """)
    Page<Account> findAccountsCreatedBetween(
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable
    );

    @Query("""
        SELECT a FROM Account a
        WHERE a.deleted = false
          AND (
            LOWER(a.displayName) LIKE LOWER(CONCAT('%', :term, '%'))
            OR (a.legalName IS NOT NULL AND LOWER(a.legalName) LIKE LOWER(CONCAT('%', :term, '%')))
          )
    """)
    Page<Account> searchByDisplayName(@Param("term") String term, Pageable pageable);

    List<Account> findByStatusAndDeletedFalse(AccountStatus status);

    List<Account> findByPaymentDueDateBeforeAndDeletedFalse(LocalDate date);

    // =========================
    // Scheduler (IDs-only)
    // =========================

    @Query("""
        SELECT a.id
          FROM Account a
         WHERE a.deleted = false
           AND a.trialEndAt <= :date
           AND a.status = :status
    """)
    List<Long> findExpiredTrialIdsNotDeleted(@Param("date") Instant date, @Param("status") AccountStatus status);

    @Query("""
        SELECT a.id
          FROM Account a
         WHERE a.deleted = false
           AND a.status = :status
           AND a.paymentDueDate < :today
    """)
    List<Long> findOverdueAccountIdsNotDeleted(@Param("status") AccountStatus status, @Param("today") LocalDate today);

    // =========================
    // Métodos antigos (mantidos)
    // =========================

 
    @Query("""
        SELECT a FROM Account a
        WHERE a.deleted = false
          AND a.status = :status
          AND a.paymentDueDate < :today
    """)
    List<Account> findOverdueAccountsNotDeleted(@Param("status") AccountStatus status, @Param("today") LocalDate today);

    // =========================
    // Control Plane account (public schema)
    // =========================

    /**
     * ✅ Semântica do domínio:
     * Conta do Control Plane = built-in/platform.
     *
     * isBuiltInAccount() := origin == BUILT_IN || type == PLATFORM
     *
     * Retorna LISTA para permitir validação explícita de cardinalidade.
     */
    @Query("""
        SELECT a FROM Account a
        WHERE a.deleted = false
          AND (a.origin = :builtInOrigin OR a.type = :platformType)
    """)
    List<Account> findControlPlaneAccounts(
            @Param("builtInOrigin") EntityOrigin builtInOrigin,
            @Param("platformType") AccountType platformType
    );

    /**
     * Helper semântico do projeto:
     * - exige exatamente 1 conta CP
     * - falha explicitamente se 0 ou >1
     */
    default Account getSingleControlPlaneAccount() {
        List<Account> list = findControlPlaneAccounts(EntityOrigin.BUILT_IN, AccountType.PLATFORM);

        if (list.isEmpty()) {
            throw new IllegalStateException("Nenhuma conta de Control Plane encontrada (esperado exatamente 1).");
        }
        if (list.size() > 1) {
            throw new IllegalStateException("Mais de uma conta de Control Plane encontrada (esperado exatamente 1).");
        }
        return list.get(0);
    }
}
