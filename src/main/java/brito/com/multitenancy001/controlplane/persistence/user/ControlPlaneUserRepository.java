package brito.com.multitenancy001.controlplane.persistence.user;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControlPlaneUserRepository extends JpaRepository<ControlPlaneUser, Long> {

    // =========================================================
    // NOT DELETED (deleted=false) ✅ default do domínio
    // =========================================================

    /** Lista usuários da conta, excluindo soft-deleted. */
    @Query("SELECT u FROM ControlPlaneUser u WHERE u.account.id = :accountId AND u.deleted = false")
    List<ControlPlaneUser> findNotDeletedByAccountId(@Param("accountId") Long accountId);

    /** Busca usuário por id + accountId, excluindo soft-deleted. */
    @Query("SELECT u FROM ControlPlaneUser u WHERE u.id = :id AND u.account.id = :accountId AND u.deleted = false")
    Optional<ControlPlaneUser> findNotDeletedByIdAndAccountId(@Param("id") Long id,
                                                              @Param("accountId") Long accountId);

    /** Busca o superadmin (não deletado) por conta. */
    @Query("""
            SELECT u
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND lower(u.username) = 'superadmin'
           """)
    Optional<ControlPlaneUser> findNotDeletedSuperAdmin(@Param("accountId") Long accountId);

    /** Busca por username + account (não deletado). */
    Optional<ControlPlaneUser> findByUsernameAndAccount_IdAndDeletedFalse(String username, Long accountId);

    /** Conta usuários (não deletados) por conta. */
    long countByAccountIdAndDeletedFalse(Long accountId);

    /** Busca por username (não deletado). */
    Optional<ControlPlaneUser> findByUsernameAndDeletedFalse(String username);

    // =========================================================
    // ENABLED = NOT DELETED + NOT suspended ✅ default segurança
    // =========================================================

    /** Lista usuários "habilitados": não deletado e não suspenso (admin nem account). */
    @Query("""
            SELECT u
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND u.suspendedByAccount = false
               AND u.suspendedByAdmin = false
           """)
    List<ControlPlaneUser> findEnabledByAccountId(@Param("accountId") Long accountId);

    /** Busca usuário "habilitado": não deletado e não suspenso (admin nem account). */
    @Query("""
            SELECT u
              FROM ControlPlaneUser u
             WHERE u.id = :id
               AND u.account.id = :accountId
               AND u.deleted = false
               AND u.suspendedByAccount = false
               AND u.suspendedByAdmin = false
           """)
    Optional<ControlPlaneUser> findEnabledByIdAndAccountId(@Param("id") Long id,
                                                           @Param("accountId") Long accountId);

    // =========================================================
    // ANY = BYPASS consciente (inclui deleted) ⚠️
    // =========================================================

    /** ⚠️ Inclui soft-deleted. Use apenas para auditoria/suporte/restore/histórico. */
    @Query("SELECT u FROM ControlPlaneUser u WHERE u.id = :id AND u.account.id = :accountId")
    Optional<ControlPlaneUser> findAnyByIdAndAccountId(@Param("id") Long id,
                                                       @Param("accountId") Long accountId);

 
    // =========================================================
    // UNICIDADE NOT DELETED (deleted=false) e case-insensitive
    // =========================================================

    @Query("""
            SELECT (COUNT(u) > 0)
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND lower(u.username) = lower(:username)
           """)
    boolean existsNotDeletedByUsernameIgnoreCase(@Param("accountId") Long accountId,
                                                 @Param("username") String username);

    @Query("""
            SELECT (COUNT(u) > 0)
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND lower(u.email) = lower(:email)
           """)
    boolean existsNotDeletedByEmailIgnoreCase(@Param("accountId") Long accountId,
                                              @Param("email") String email);

    @Query("""
            SELECT (COUNT(u) > 0)
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND lower(u.username) = lower(:username)
               AND u.id <> :userId
           """)
    boolean existsOtherNotDeletedByUsernameIgnoreCase(@Param("accountId") Long accountId,
                                                      @Param("username") String username,
                                                      @Param("userId") Long userId);

    @Query("""
            SELECT (COUNT(u) > 0)
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND lower(u.email) = lower(:email)
               AND u.id <> :userId
           """)
    boolean existsOtherNotDeletedByEmailIgnoreCase(@Param("accountId") Long accountId,
                                                   @Param("email") String email,
                                                   @Param("userId") Long userId);
    
    
    

 /** Enabled por id: deleted=false e não suspenso (admin nem account). */
 @Query("""
     select u
     from ControlPlaneUser u
     where u.id = :id
       and u.deleted = false
       and u.suspendedByAdmin = false
       and u.suspendedByAccount = false
 """)
 Optional<ControlPlaneUser> findEnabledById(@Param("id") Long id);

    
}
