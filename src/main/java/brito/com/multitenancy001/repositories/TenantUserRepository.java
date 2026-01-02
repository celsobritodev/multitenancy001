package brito.com.multitenancy001.repositories;

import brito.com.multitenancy001.entities.tenant.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {
	
	
	Optional<TenantUser> findByUsernameAndDeletedFalse(String username);
	
	Optional<TenantUser> findByEmailAndAccountIdAndDeletedFalse(String email, Long accountId);
	


	

    // ==========================
    // Login / Auth
    // ==========================
    Optional<TenantUser> findByUsernameAndAccountId(String username, Long accountId);

    // (se você quiser filtrar deletados no login)
    Optional<TenantUser> findByUsernameAndAccountIdAndDeletedFalse(String username, Long accountId);

    Optional<TenantUser> findByEmailAndDeletedFalse(String email);

    // ==========================
    // Unicidade por conta (tenant)
    // ==========================
    boolean existsByUsernameAndAccountId(String username, Long accountId);

    boolean existsByEmailAndAccountId(String email, Long accountId);

    boolean existsByEmailAndAccountIdAndIdNot(String email, Long accountId, Long id);

    // ==========================
    // Listagens (Tenant)
    // ==========================
    List<TenantUser> findByAccountId(Long accountId);

    List<TenantUser> findByAccountIdAndDeletedFalse(Long accountId);

    List<TenantUser> findByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);

    // ==========================
    // Contagem / Limites
    // ==========================
    long countByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);

    // ==========================
    // Busca por ID com scoping de conta
    // ==========================
    Optional<TenantUser> findByIdAndAccountId(Long id, Long accountId);

    // (se quiser reforçar deletado false)
    Optional<TenantUser> findByIdAndAccountIdAndDeletedFalse(Long id, Long accountId);
}
