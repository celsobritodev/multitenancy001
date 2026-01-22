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
    // NOT DELETED (deleted=false)  ✅ padrão para "exists/unique/list"
    // =========================================================

    @Query("SELECT u FROM ControlPlaneUser u WHERE u.account.id = :accountId AND u.deleted = false")
    List<ControlPlaneUser> findNotDeletedByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT u FROM ControlPlaneUser u WHERE u.id = :id AND u.account.id = :accountId AND u.deleted = false")
    Optional<ControlPlaneUser> findNotDeletedByIdAndAccountId(@Param("id") Long id,
                                                              @Param("accountId") Long accountId);

    @Query("""
            SELECT u
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND lower(u.username) = 'superadmin'
           """)
    Optional<ControlPlaneUser> findNotDeletedSuperAdmin(@Param("accountId") Long accountId);

    Optional<ControlPlaneUser> findByUsernameAndAccount_IdAndDeletedFalse(String username, Long accountId);

    long countByAccountIdAndDeletedFalse(Long accountId);

    Optional<ControlPlaneUser> findByIdAndAccountId(Long id, Long accountId);

    Optional<ControlPlaneUser> findByUsernameAndDeletedFalse(String username);

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

    // =========================================================
    // ENABLED (deleted=false AND NOT suspended)  ✅ opcional/recomendado
    // =========================================================

    @Query("""
            SELECT u
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND u.suspendedByAccount = false
               AND u.suspendedByAdmin = false
           """)
    List<ControlPlaneUser> findEnabledByAccountId(@Param("accountId") Long accountId);

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

}
