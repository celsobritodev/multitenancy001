package brito.com.multitenancy001.controlplane.persistence.account;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByTypeAndDeletedFalse(AccountType type);

    boolean existsByCompanyEmailAndDeletedFalse(String companyEmail);

    boolean existsByCompanyDocTypeAndCompanyDocNumberAndDeletedFalse(
            TaxIdType companyDocType, String companyDocNumber
    );

    List<Account> findAllByDeletedFalse();

    Optional<Account> findBySlugAndDeletedFalse(String slug);
    Optional<Account> findByIdAndDeletedFalse(Long id);

    List<Account> findByStatus(AccountStatus status);

    List<Account> findByPaymentDueDateBefore(LocalDateTime date);

    @Query("SELECT a FROM Account a WHERE a.trialEndDate <= :date AND a.status = :status")
    List<Account> findExpiredTrials(@Param("date") LocalDateTime date, @Param("status") AccountStatus status);

    @Query("SELECT a FROM Account a WHERE a.status = :status AND a.paymentDueDate < :today")
    List<Account> findOverdueAccounts(@Param("status") AccountStatus status, @Param("today") LocalDateTime today);

    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.status IN :statuses")
    List<Account> findByStatuses(@Param("statuses") List<AccountStatus> statuses);

    // =========================================================
    // ✅ MÉTODOS PARA ADMIN (com paginação onde faz sentido)
    // =========================================================

    @Query("SELECT COUNT(a) FROM Account a WHERE a.deleted = false AND a.status = 'ACTIVE'")
    long countActiveAccounts();

    boolean existsByNameAndDeletedFalse(String name);

    boolean existsBySchemaNameAndDeletedFalse(String schemaName);

    Optional<Account> findBySlugAndDeletedFalseIgnoreCase(String slug);

    // latest (paginado)
    Page<Account> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    // by status (paginado)
    Page<Account> findByStatusAndDeletedFalse(AccountStatus status, Pageable pageable);

    // created between (paginado)
    @Query("SELECT a FROM Account a " +
           "WHERE a.deleted = false AND a.createdAt BETWEEN :start AND :end")
    Page<Account> findAccountsCreatedBetween(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             Pageable pageable);

    // search by name (paginado)
    @Query("SELECT a FROM Account a " +
           "WHERE a.deleted = false " +
           "AND LOWER(a.name) LIKE LOWER(CONCAT('%', :term, '%'))")
    Page<Account> searchByName(@Param("term") String term, Pageable pageable);
}
