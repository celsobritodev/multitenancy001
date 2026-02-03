package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.time.LocalDateTime;
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
import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // =========================================================
    // EXISTS / UNIQUE (padrão: NOT DELETED)
    // =========================================================

    boolean existsByTaxCountryCodeAndTaxIdTypeAndTaxIdNumberAndDeletedFalse(
            String taxCountryCode, TaxIdType taxIdType, String taxIdNumber
    );


    boolean existsByLoginEmailAndDeletedFalse(String loginEmail);

    // ❌ REMOVIDO: existsByTaxIdTypeAndTaxIdNumberAndDeletedFalse(...)
    // Motivo: a regra correta agora é única por (taxCountryCode, taxIdType, taxIdNumber).

    // =========================================================
    // DEFAULT DE DOMÍNIO: NOT DELETED (deleted=false)
    // =========================================================

    List<Account> findAllByDeletedFalse();

    Optional<Account> findBySlugAndDeletedFalse(String slug);

    Optional<Account> findBySlugAndDeletedFalseIgnoreCase(String slug);

    // =========================================================
    // PROJECTIONS (para evitar expor entidade fora do CP)
    // =========================================================

    Optional<AccountResolverProjection> findProjectionByIdAndDeletedFalse(Long id);

    Optional<AccountResolverProjection> findProjectionBySlugAndDeletedFalseIgnoreCase(String slug);

    Optional<Account> findByIdAndDeletedFalse(Long id);

   
    // =========================================================
    // DEFAULT DE SEGURANÇA: ENABLED (operacional)
    // enabled = NOT DELETED + status operacional
    // =========================================================

    @Query("""
            SELECT a
              FROM Account a
             WHERE a.id = :id
               AND a.deleted = false
               AND a.status IN ('ACTIVE', 'FREE_TRIAL')
           """)
    Optional<Account> findEnabledById(@Param("id") Long id);

    // =========================================================
    // BYPASS CONSCIENTE: ANY (inclui deleted)
    // =========================================================

    Optional<Account> findAnyById(Long id);

    // =========================================================
    // QUERIES OPERACIONAIS (NOT DELETED)
    // =========================================================

    @Query("SELECT COUNT(a) FROM Account a WHERE a.deleted = false AND a.status IN :statuses")
    long countByStatusesAndDeletedFalse(@Param("statuses") List<AccountStatus> statuses);

    default long countOperationalAccounts() {
        return countByStatusesAndDeletedFalse(List.of(AccountStatus.ACTIVE, AccountStatus.FREE_TRIAL));
    }

   
    Page<Account> findByStatusAndDeletedFalse(AccountStatus status, Pageable pageable);

    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.createdAt BETWEEN :start AND :end")
    Page<Account> findAccountsCreatedBetween(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             Pageable pageable);

    @Query("""
        SELECT a FROM Account a
        WHERE a.deleted = false
          AND (
            LOWER(a.displayName) LIKE LOWER(CONCAT('%', :term, '%'))
            OR (a.legalName IS NOT NULL AND LOWER(a.legalName) LIKE LOWER(CONCAT('%', :term, '%')))
          )
    """)
    Page<Account> searchByDisplayName(@Param("term") String term, Pageable pageable);

    // =========================================================
    // QUERIES "ANTIGAS" -> manter por compatibilidade
    // =========================================================

    List<Account> findByStatusAndDeletedFalse(AccountStatus status);

    List<Account> findByPaymentDueDateBeforeAndDeletedFalse(LocalDateTime date);

    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.trialEndDate <= :date AND a.status = :status")
    List<Account> findExpiredTrialsNotDeleted(@Param("date") LocalDateTime date, @Param("status") AccountStatus status);

    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.status = :status AND a.paymentDueDate < :today")
    List<Account> findOverdueAccountsNotDeleted(@Param("status") AccountStatus status, @Param("today") LocalDateTime today);
}
