package brito.com.multitenancy001.controlplane.persistence.account;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.domain.account.AccountType;
import brito.com.multitenancy001.controlplane.domain.account.TaxIdType;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // =========================================================
    // EXISTS / UNIQUE (padrão: NOT DELETED)
    // =========================================================

    boolean existsByTaxCountryCodeAndTaxIdTypeAndTaxIdNumberAndDeletedFalse(
            String taxCountryCode, TaxIdType taxIdType, String taxIdNumber
    );

    boolean existsByTypeAndDeletedFalse(AccountType type);

    boolean existsByLoginEmailAndDeletedFalse(String loginEmail);

    boolean existsByTaxIdTypeAndTaxIdNumberAndDeletedFalse(TaxIdType taxIdType, String taxIdNumber);

    // =========================================================
    // DEFAULT DE DOMÍNIO: NOT DELETED (deleted=false)
    // =========================================================

    /** Lista todas as contas não deletadas. */
    List<Account> findAllByDeletedFalse();

    /** Busca por slug (não deletado). */
    Optional<Account> findBySlugAndDeletedFalse(String slug);

    /** Busca por slug ignoreCase (não deletado). */
    Optional<Account> findBySlugAndDeletedFalseIgnoreCase(String slug);
    
    // =========================================================
    // PROJECTIONS (para evitar expor entidade fora do CP)
    // =========================================================

    Optional<AccountResolverProjection> findProjectionBySlugAndDeletedFalse(String slug);

    Optional<AccountResolverProjection> findProjectionBySlugAndDeletedFalseIgnoreCase(String slug);


    /** Busca por id (não deletado). Default para leitura normal. */
    Optional<Account> findByIdAndDeletedFalse(Long id);

    /** Contas por lista de status (não deletado). */
    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.status IN :statuses")
    List<Account> findByStatuses(@Param("statuses") List<AccountStatus> statuses);

    // =========================================================
    // DEFAULT DE SEGURANÇA: ENABLED (operacional)
    // enabled = NOT DELETED + status operacional
    // =========================================================

    /**
     * Conta "enabled/operacional": não deletada e status operacional.
     * Use em fluxos que precisam garantir conta em operação (ex.: login, ações sensíveis).
     */
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

    /**
     * ⚠️ BYPASS: pode incluir soft-deleted.
     * Use apenas para auditoria/suporte/restore.
     */
    Optional<Account> findAnyById(Long id);

    // =========================================================
    // QUERIES OPERACIONAIS (NOT DELETED)
    // =========================================================

    @Query("SELECT COUNT(a) FROM Account a WHERE a.deleted = false AND a.status = :status")
    long countByStatusAndDeletedFalse(@Param("status") AccountStatus status);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.deleted = false AND a.status IN :statuses")
    long countByStatusesAndDeletedFalse(@Param("statuses") List<AccountStatus> statuses);

    default long countOperationalAccounts() {
        return countByStatusesAndDeletedFalse(List.of(AccountStatus.ACTIVE, AccountStatus.FREE_TRIAL));
    }

    Page<Account> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<Account> findByStatusAndDeletedFalse(AccountStatus status, Pageable pageable);

    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.createdAt BETWEEN :start AND :end")
    Page<Account> findAccountsCreatedBetween(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             Pageable pageable);

    // busca em displayName e legalName
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
    // QUERIES "ANTIGAS" (potencialmente unsafe) -> manter por compatibilidade
    // =========================================================

    /** Contas por status, NOT DELETED. Preferir esta. */
    List<Account> findByStatusAndDeletedFalse(AccountStatus status);

    /** Contas com vencimento antes de X, NOT DELETED. Preferir esta. */
    List<Account> findByPaymentDueDateBeforeAndDeletedFalse(LocalDateTime date);

    /** Trials expirados, NOT DELETED. Preferir esta. */
    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.trialEndDate <= :date AND a.status = :status")
    List<Account> findExpiredTrialsNotDeleted(@Param("date") LocalDateTime date, @Param("status") AccountStatus status);

    /** Contas em atraso, NOT DELETED. Preferir esta. */
    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.status = :status AND a.paymentDueDate < :today")
    List<Account> findOverdueAccountsNotDeleted(@Param("status") AccountStatus status, @Param("today") LocalDateTime today);

    

  

}
