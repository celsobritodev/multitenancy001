package brito.com.multitenancy001.tenant.persistence.user;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.tenant.domain.user.TenantUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {
	
	
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

    // ✅ Suspender 1 usuário manualmente pelo ADMIN do TENANT
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

  

	
    
    Optional<TenantUser> findByPasswordResetTokenAndAccountId(String passwordResetToken, Long accountId);

    Optional<TenantUser> findByUsernameAndDeletedFalse(String username);

    Optional<TenantUser> findByEmailAndAccountId(String email, Long accountId);

    Optional<TenantUser> findByEmailAndAccountIdAndDeletedFalse(String email, Long accountId);

    Optional<TenantUser> findByUsernameAndAccountId(String username, Long accountId);

    // (se você quiser filtrar deletados no login)
    Optional<TenantUser> findByUsernameAndAccountIdAndDeletedFalse(String username, Long accountId);

    boolean existsByUsernameAndAccountId(String username, Long accountId);

    boolean existsByEmailAndAccountId(String email, Long accountId);

    boolean existsByEmailAndAccountIdAndIdNot(String email, Long accountId, Long id);

    // ==========================
    // Listagens (Tenant)
    // ==========================
    List<TenantUser> findByAccountId(Long accountId);

    List<TenantUser> findByAccountIdAndDeletedFalse(Long accountId);

    // CORREÇÃO: Removido o método que usa "ActiveTrue" pois não existe na entidade
    // List<TenantUser> findByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);
    
    // NOVO: Método para buscar usuários ativos usando lógica customizada
    @Query("SELECT u FROM TenantUser u WHERE u.accountId = :accountId " +
           "AND u.deleted = false " +
           "AND u.suspendedByAccount = false " +
           "AND u.suspendedByAdmin = false")
    List<TenantUser> findActiveUsersByAccount(@Param("accountId") Long accountId);

    // ==========================
    // Contagem / Limites
    // ==========================
    // CORREÇÃO: Removido método que não funciona
    // long countByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);
    
    // NOVO: Contagem de usuários ativos
    @Query("SELECT COUNT(u) FROM TenantUser u WHERE u.accountId = :accountId " +
           "AND u.deleted = false " +
           "AND u.suspendedByAccount = false " +
           "AND u.suspendedByAdmin = false")
    long countActiveUsersByAccount(@Param("accountId") Long accountId);

    // ==========================
    // Busca por ID com scoping de conta
    // ==========================
    Optional<TenantUser> findByIdAndAccountId(Long id, Long accountId);

    // (se quiser reforçar deletado false)
    Optional<TenantUser> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);
}