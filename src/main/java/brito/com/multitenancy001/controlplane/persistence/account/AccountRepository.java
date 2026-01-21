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
	
	boolean existsByTaxCountryCodeAndTaxIdTypeAndTaxIdNumberAndDeletedFalse(
	        String taxCountryCode, TaxIdType taxIdType, String taxIdNumber
	);
	
	@Query("SELECT COUNT(a) FROM Account a WHERE a.deleted = false AND a.status = :status")
	long countByStatusAndDeletedFalse(@Param("status") AccountStatus status);



    boolean existsByTypeAndDeletedFalse(AccountType type);

    boolean existsByLoginEmailAndDeletedFalse(String loginEmail);

    boolean existsByTaxIdTypeAndTaxIdNumberAndDeletedFalse(TaxIdType taxIdType, String taxIdNumber);

    List<Account> findAllByDeletedFalse();

    Optional<Account> findBySlugAndDeletedFalse(String slug);

    Optional<Account> findBySlugAndDeletedFalseIgnoreCase(String slug);

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
    // ADMIN (mantendo os nomes que seu service chama)
    // =========================================================

   
    
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

    // ✅ para manter compatível com AccountLifecycleService.searchAccountsByName()
    // agora busca em displayName e também em legalName
    @Query("""
        SELECT a FROM Account a
        WHERE a.deleted = false
          AND (
            LOWER(a.displayName) LIKE LOWER(CONCAT('%', :term, '%'))
            OR (a.legalName IS NOT NULL AND LOWER(a.legalName) LIKE LOWER(CONCAT('%', :term, '%')))
          )
    """)
    Page<Account> searchByDisplayName(@Param("term") String term, Pageable pageable);
}
