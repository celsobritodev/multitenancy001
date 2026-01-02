package brito.com.multitenancy001.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.platform.domain.tenant.TenantAccount;
import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<TenantAccount, Long> {


	
	
	List<TenantAccount> findAllByDeletedFalse();

	List<TenantAccount> findByDeletedFalseOrderByCreatedAtDesc();

	Optional<TenantAccount> findBySlugAndDeletedFalse(String slug);

	Optional<TenantAccount> findBySlug(String slug);

	Optional<TenantAccount> findByName(String name);

	Optional<TenantAccount> findBySchemaName(String schemaName);

	List<TenantAccount> findByStatus(TenantAccountStatus status);

	List<TenantAccount> findByPaymentDueDateBefore(LocalDateTime date);

	List<TenantAccount> findByDeletedFalse();

	List<TenantAccount> findByStatusAndDeletedFalse(TenantAccountStatus status);

	Optional<TenantAccount> findByIdAndDeletedFalse(Long id);

	@Query("SELECT a FROM TenantAccount  a WHERE a.trialEndDate <= :date AND a.status = :status")
	List<TenantAccount> findExpiredTrials(@Param("date") LocalDateTime date, @Param("status") TenantAccountStatus status); // ✅
																												// CORRIGIDO:
																												// Usando
																												// parâmetro

	@Query("SELECT COUNT(a) FROM TenantAccount  a WHERE a.deleted = false")
	Long countActiveAccounts();

	@Query("SELECT a FROM TenantAccount  a WHERE a.status = :status AND a.paymentDueDate < :today")
	List<TenantAccount> findOverdueAccounts(@Param("status") TenantAccountStatus status, @Param("today") LocalDateTime today); // ✅
																													// CORRIGIDO:
																													// Usando
																													// parâmetro

	boolean existsByNameAndDeletedFalse(String name);

	boolean existsBySchemaNameAndDeletedFalse(String schemaName);

	@Query("SELECT a FROM TenantAccount  a WHERE a.createdAt BETWEEN :startDate AND :endDate")
	List<TenantAccount> findAccountsCreatedBetween(@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate);

	@Query("SELECT a FROM TenantAccount  a WHERE a.deleted = false AND a.status IN :statuses")
	List<TenantAccount> findByStatuses(@Param("statuses") List<TenantAccountStatus> statuses);

	@Query("SELECT a FROM TenantAccount  a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND a.deleted = false")
	List<TenantAccount> searchByName(@Param("searchTerm") String searchTerm);
}