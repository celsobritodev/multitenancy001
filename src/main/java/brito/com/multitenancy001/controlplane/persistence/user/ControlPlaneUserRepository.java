package brito.com.multitenancy001.controlplane.persistence.user;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControlPlaneUserRepository extends JpaRepository<ControlPlaneUser, Long> {

    // =========================================================
    // BASICS
    // =========================================================

    Optional<ControlPlaneUser> findByEmailAndDeletedFalse(String email);

    Optional<ControlPlaneUser> findByEmailAndAccount_IdAndDeletedFalse(String email, Long accountId);

    // =========================================================
    // NOT DELETED (deleted=false) -> default do domínio
    // =========================================================

    @Query("SELECT u FROM ControlPlaneUser u WHERE u.account.id = :accountId AND u.deleted = false")
    List<ControlPlaneUser> findNotDeletedByAccountId(@Param("accountId") Long accountId);

    @Query("SELECT u FROM ControlPlaneUser u WHERE u.id = :id AND u.account.id = :accountId AND u.deleted = false")
    Optional<ControlPlaneUser> findNotDeletedByIdAndAccountId(@Param("id") Long id,
                                                              @Param("accountId") Long accountId);

    /**
     * Agora: usuário BUILT_IN + role CONTROLPLANE_OWNER.
     */
    @Query("""
            SELECT u
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND u.origin = :origin
               AND u.role = :role
           """)
    Optional<ControlPlaneUser> findNotDeletedBuiltInOwner(@Param("accountId") Long accountId,
                                                         @Param("origin") EntityOrigin  origin,
                                                         @Param("role") ControlPlaneRole role);

    long countByAccount_IdAndDeletedFalse(Long accountId);

    // =========================================================
    // ENABLED = NOT DELETED + NOT suspended -> default segurança
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

    // =========================================================
    // ANY = BYPASS consciente (inclui deleted) ⚠️
    // =========================================================

    @Query("SELECT u FROM ControlPlaneUser u WHERE u.id = :id AND u.account.id = :accountId")
    Optional<ControlPlaneUser> findAnyByIdAndAccountId(@Param("id") Long id,
                                                       @Param("accountId") Long accountId);

    // =========================================================
    // UNICIDADE NOT DELETED (deleted=false)
    //
    // IMPORTANTE:
    // - A coluna email é CITEXT (case-insensitive no Postgres).
    // - Logo a comparação pode (e deve) ser direta: u.email = :email
    // - A normalização (trim/lower) deve acontecer fora (EmailNormalizer).
    // =========================================================

    @Query("""
            SELECT (COUNT(u) > 0)
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND u.email = :email
           """)
    boolean existsNotDeletedByEmailIgnoreCase(@Param("accountId") Long accountId,
                                              @Param("email") String email);

    @Query("""
            SELECT (COUNT(u) > 0)
              FROM ControlPlaneUser u
             WHERE u.account.id = :accountId
               AND u.deleted = false
               AND u.email = :email
               AND u.id <> :userId
           """)
    boolean existsOtherNotDeletedByEmailIgnoreCase(@Param("accountId") Long accountId,
                                                   @Param("email") String email,
                                                   @Param("userId") Long userId);

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
