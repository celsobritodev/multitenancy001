package brito.com.multitenancy001.tenant.persistence.user;

import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

	Optional<TenantUser> findByEmailAndDeletedFalse(String email);

	
	
	
    // ==========================
    // Bulk operations (Tenant)
    // ==========================

    // ✅ Suspende por CONTA (sem mexer na suspensão por admin)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.suspendedByAccount = true
         where u.accountId = :accountId
           and u.deleted = false
    """)
    int suspendAllByAccount(@Param("accountId") Long accountId);

    // ✅ Reativa por CONTA (sem mexer na suspensão por admin)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.suspendedByAccount = false
         where u.accountId = :accountId
           and u.deleted = false
    """)
    int unsuspendAllByAccount(@Param("accountId") Long accountId);

    // ✅ Suspende/Reativa por ADMIN (1 usuário)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.suspendedByAdmin = :suspended
         where u.id = :userId
           and u.accountId = :accountId
           and u.deleted = false
    """)
    int setSuspendedByAdmin(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId,
            @Param("suspended") boolean suspended
    );

    // ==========================
    // Finds (login / reset)
    // ==========================

    Optional<TenantUser> findByPasswordResetTokenAndAccountId(String passwordResetToken, Long accountId);



    Optional<TenantUser> findByEmailAndAccountId(String email, Long accountId);

    Optional<TenantUser> findByEmailAndAccountIdAndDeletedFalse(String email, Long accountId);



    // ==========================
    // Exists
    // ==========================

 
    boolean existsByEmailAndAccountId(String email, Long accountId);

    boolean existsByEmailAndAccountIdAndIdNot(String email, Long accountId, Long id);

    // ==========================
    // Lists (Tenant)
    // ==========================

    List<TenantUser> findByAccountId(Long accountId);

    List<TenantUser> findByAccountIdAndDeletedFalse(Long accountId);

    // "Ativos" = não deletado e não suspenso (por conta e por admin)
    List<TenantUser> findByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(Long accountId);

    // ✅ Alias legível (evita nome gigante espalhado no projeto)
    default List<TenantUser> findEnabledUsersByAccount(Long accountId) {
        return findByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(accountId);
    }

    // ==========================
    // Counts / Limits
    // ==========================

    // SEATS (Política A): conta todos não deletados
    long countByAccountIdAndDeletedFalse(Long accountId);

    // ACTIVE_USERS_ONLY: conta não deletados e não suspensos
    long countByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(Long accountId);

    // ✅ Alias legível (opcional)
    default long countEnabledUsersByAccount(Long accountId) {
        return countByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(accountId);
    }

    // ==========================
    // Scoped ID
    // ==========================

   

    
    
    // ==========================
    // ⚠️ Bypass consciente: INCLUDING DELETED
    // Só use para auditoria/suporte/restore.
    // ==========================

    /** ⚠️ Inclui soft-deleted. Use apenas para auditoria/suporte/restore. */
    @Query("select u from TenantUser u where u.id = :id and u.accountId = :accountId")
    Optional<TenantUser> findIncludingDeletedByIdAndAccountId(@Param("id") Long id,
                                                              @Param("accountId") Long accountId);
    
    
    // ==========================
    // Scoped ID
    // ==========================

    /**
     * DEFAULT (NotDeleted): leitura normal do domínio.
     */
    Optional<TenantUser> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);

    /**
     * DEFAULT (Enabled): login/uso ativo.
     * enabled = NOT DELETED + NOT suspended (account/admin)
     */
    @Query("""
        select u from TenantUser u
        where u.id = :id
          and u.accountId = :accountId
          and u.deleted = false
          and u.suspendedByAccount = false
          and u.suspendedByAdmin = false
    """)
    Optional<TenantUser> findEnabledByIdAndAccountId(@Param("id") Long id,
                                                     @Param("accountId") Long accountId);

    // ==========================
    // ⚠️ ANY (bypass consciente): INCLUDING DELETED
    // ==========================

    /** ⚠️ Inclui soft-deleted. Use apenas para auditoria/suporte/restore. */
    @Query("select u from TenantUser u where u.id = :id and u.accountId = :accountId")
    Optional<TenantUser> findAnyByIdAndAccountId(@Param("id") Long id,
                                                 @Param("accountId") Long accountId);
    

    /** Enabled por id: deleted=false e não suspenso (admin nem account). */
    @Query("""
        select u
        from TenantUser u
        where u.id = :id
          and u.deleted = false
          and u.suspendedByAdmin = false
          and u.suspendedByAccount = false
    """)
    Optional<TenantUser> findEnabledById(@Param("id") Long id);

   

   
}
