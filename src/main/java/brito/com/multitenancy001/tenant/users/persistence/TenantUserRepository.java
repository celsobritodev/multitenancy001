package brito.com.multitenancy001.tenant.users.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.tenant.users.domain.TenantUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    // =========================================================
    // LOGIN / IDENTIDADE (email)
    // =========================================================

    Optional<TenantUser> findByEmailAndDeletedFalse(String email);



    Optional<TenantUser> findByEmailAndAccountIdAndDeletedFalse(String email, Long accountId);

    boolean existsByEmailAndAccountId(String email, Long accountId);



    // =========================================================
    // PASSWORD RESET
    // =========================================================

    Optional<TenantUser> findByPasswordResetTokenAndAccountId(String passwordResetToken, Long accountId);

    // =========================================================
    // LISTS
    // =========================================================

    List<TenantUser> findByAccountId(Long accountId);

    List<TenantUser> findByAccountIdAndDeletedFalse(Long accountId);

    /**
     * Enabled = NOT deleted + NOT suspendedByAccount + NOT suspendedByAdmin
     */
    List<TenantUser> findByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(Long accountId);

    /**
     * Alias legível (evita nome gigante espalhado no projeto).
     */
    default List<TenantUser> findEnabledUsersByAccount(Long accountId) {
        return findByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(accountId);
    }

    // =========================================================
    // COUNTS / LIMITS
    // =========================================================

    long countByAccountIdAndDeletedFalse(Long accountId);

    long countByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(Long accountId);

    default long countEnabledUsersByAccount(Long accountId) {
       return countByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(accountId);
    }

    // =========================================================
    // SCOPED ID (READ)
    // =========================================================

    /**
     * DEFAULT (NotDeleted): leitura normal do domínio.
     */
    Optional<TenantUser> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);

    /**
     * DEFAULT (Enabled): login/uso ativo.
     */
    @Query("""
        select u from TenantUser u
        where u.id = :id
          and u.accountId = :accountId
          and u.deleted = false
          and u.suspendedByAccount = false
          and u.suspendedByAdmin = false
    """)
    Optional<TenantUser> findEnabledByIdAndAccountId(
            @Param("id") Long id,
            @Param("accountId") Long accountId
    );

    /**
     * ⚠️ Inclui soft-deleted. Use apenas para auditoria/suporte/restore.
     */
    @Query("""
        select u from TenantUser u
        where u.id = :id
          and u.accountId = :accountId
    """)
    Optional<TenantUser> findIncludingDeletedByIdAndAccountId(
            @Param("id") Long id,
            @Param("accountId") Long accountId
    );

    /**
     * Alias (mesma coisa do findIncludingDeletedByIdAndAccountId).
     * Mantido se houver código chamando "findAny...".
     */
    default Optional<TenantUser> findAnyByIdAndAccountId(Long id, Long accountId) {
        return findIncludingDeletedByIdAndAccountId(id, accountId);
    }

    // =========================================================
    // UPDATE: SUSPENSÕES
    // =========================================================

    /**
     * Suspende/Reativa por ADMIN (1 usuário) - não mexe em suspendedByAccount.
     */
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

    /**
     * Suspende/Reativa por CONTA (1 usuário) - não mexe em suspendedByAdmin.
     * ✅ Este método é necessário porque o TenantUserTxService chama ele.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.suspendedByAccount = :suspended
         where u.id = :userId
           and u.accountId = :accountId
           and u.deleted = false
    """)
    int setSuspendedByAccount(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId,
            @Param("suspended") boolean suspended
    );

    /**
     * Suspende TODOS por CONTA (bulk) - não mexe em suspendedByAdmin.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.suspendedByAccount = true
         where u.accountId = :accountId
           and u.deleted = false
    """)
    int suspendAllByAccount(@Param("accountId") Long accountId);

    /**
     * Reativa TODOS por CONTA (bulk) - não mexe em suspendedByAdmin.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.suspendedByAccount = false
         where u.accountId = :accountId
           and u.deleted = false
    """)
    int unsuspendAllByAccount(@Param("accountId") Long accountId);

    // =========================================================
    // UPDATE: SOFT DELETE / RESTORE (bulk)
    // =========================================================

    /**
     * Soft-delete em massa por conta.
     * ✅ Necessário porque o TenantUserProvisioningFacade chama este método.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.deleted = true,
               u.deletedAt = :deletedAt
         where u.accountId = :accountId
           and u.deleted = false
    """)
    int softDeleteAllByAccount(
            @Param("accountId") Long accountId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * Restore em massa por conta.
     * ✅ Necessário porque o TenantUserProvisioningFacade chama este método.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.deleted = false,
               u.deletedAt = null
         where u.accountId = :accountId
           and u.deleted = true
    """)
    int restoreAllByAccount(@Param("accountId") Long accountId);
    
    // ✅ NOVO: grava last_login no login
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        update TenantUser u
           set u.lastLogin = :lastLogin
         where u.id = :userId
           and u.deleted = false
    """)
    int updateLastLogin(
            @Param("userId") Long userId,
            @Param("lastLogin") LocalDateTime lastLogin
    );
    
}
