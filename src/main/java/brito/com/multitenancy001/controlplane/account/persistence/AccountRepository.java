package brito.com.multitenancy001.controlplane.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.domain.account.DocumentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByCompanyEmailAndDeletedFalse(String companyEmail);

    // ✅ docType + docNumber juntos
    boolean existsByCompanyDocTypeAndCompanyDocNumberAndDeletedFalse(DocumentType companyDocType, String companyDocNumber);

    List<Account> findAllByDeletedFalse();
    List<Account> findByDeletedFalseOrderByCreatedAtDesc();

    Optional<Account> findBySlugAndDeletedFalse(String slug);
    Optional<Account> findByIdAndDeletedFalse(Long id);

    List<Account> findByStatus(AccountStatus status);
    List<Account> findByStatusAndDeletedFalse(AccountStatus status);
    List<Account> findByPaymentDueDateBefore(LocalDateTime date);

    @Query("SELECT a FROM Account a WHERE a.trialEndDate <= :date AND a.status = :status")
    List<Account> findExpiredTrials(@Param("date") LocalDateTime date, @Param("status") AccountStatus status);

    @Query("SELECT COUNT(a) FROM Account a WHERE a.deleted = false")
    Long countActiveAccounts();

    @Query("SELECT a FROM Account a WHERE a.status = :status AND a.paymentDueDate < :today")
    List<Account> findOverdueAccounts(@Param("status") AccountStatus status, @Param("today") LocalDateTime today);

    boolean existsByNameAndDeletedFalse(String name);
    boolean existsBySchemaNameAndDeletedFalse(String schemaName);

    @Query("SELECT a FROM Account a WHERE a.createdAt BETWEEN :startDate AND :endDate")
    List<Account> findAccountsCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);

    @Query("SELECT a FROM Account a WHERE a.deleted = false AND a.status IN :statuses")
    List<Account> findByStatuses(@Param("statuses") List<AccountStatus> statuses);

    @Query("SELECT a FROM Account a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND a.deleted = false")
    List<Account> searchByName(@Param("searchTerm") String searchTerm);
}
