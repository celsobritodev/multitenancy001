package brito.com.multitenancy001.tenant.users.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;

import java.time.Instant;
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
    // OWNER COUNTS / INVARIANTS (mínimo 1 OWNER)
    // =========================================================

    @Query("""
        select count(u) from TenantUser u
         where u.accountId = :accountId
           and u.deleted = false
           and u.suspendedByAccount = false
           and u.suspendedByAdmin = false
           and u.role = :ownerRole
    """)
    long countActiveOwnersByAccountId(
            @Param("accountId") Long accountId,
            @Param("ownerRole") TenantRole ownerRole
    );

    @Query("""
        select count(u) from TenantUser u
         where u.accountId = :accountId
           and u.deleted = false
           and u.role = :role
    """)
    long countNotDeletedByAccountIdAndRole(
            @Param("accountId") Long accountId,
            @Param("role") TenantRole role
    );

    // =========================================================
    // SCOPED ID (READ)
    // =========================================================

    Optional<TenantUser> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);

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

    @Query("""
        select u from TenantUser u
        where u.id = :id
          and u.accountId = :accountId
    """)
    Optional<TenantUser> findIncludingDeletedByIdAndAccountId(
            @Param("id") Long id,
            @Param("accountId") Long accountId
    );

    default Optional<TenantUser> findAnyByIdAndAccountId(Long id, Long accountId) {
        return findIncludingDeletedByIdAndAccountId(id, accountId);
    }

    // =========================================================
    // UPDATE: SUSPENSÕES
    // =========================================================

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @TenantTx
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @TenantTx
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @TenantTx
    @Query("""
        update TenantUser u
           set u.suspendedByAccount = true
         where u.accountId = :accountId
           and u.deleted = false
           and u.role <> :excludedRole
    """)
    int suspendAllByAccountExceptRole(
            @Param("accountId") Long accountId,
            @Param("excludedRole") TenantRole excludedRole
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @TenantTx
    @Query("""
        update TenantUser u
           set u.suspendedByAccount = false
         where u.accountId = :accountId
           and u.deleted = false
    """)
    int unsuspendAllByAccount(@Param("accountId") Long accountId);

    /**
     * ✅ NOVO (SAFE): soft-delete em massa por conta, EXCETO uma role (ex: TENANT_OWNER).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @TenantTx
    @Query("""
        update TenantUser u
           set u.deleted = true,
               u.audit.deletedAt = :deletedAt
         where u.accountId = :accountId
           and u.deleted = false
           and u.role <> :excludedRole
    """)
    int softDeleteAllByAccountExceptRole(
            @Param("accountId") Long accountId,
            @Param("excludedRole") TenantRole excludedRole,
            @Param("deletedAt") Instant deletedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @TenantTx
    @Query("""
        update TenantUser u
           set u.deleted = false,
               u.audit.deletedAt = null
         where u.accountId = :accountId
           and u.deleted = true
    """)
    int restoreAllByAccount(@Param("accountId") Long accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @TenantTx
    @Query("""
        update TenantUser u
           set u.lastLoginAt = :lastLogin
         where u.id = :userId
           and u.deleted = false
    """)
    int updateLastLogin(
            @Param("userId") Long userId,
            @Param("lastLogin") Instant lastLogin
    );
}
