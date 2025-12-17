package brito.com.multitenancy001.repositories;





import brito.com.multitenancy001.entities.tenant.UserTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTenantRepository extends JpaRepository<UserTenant, Long> {
    
    Optional<UserTenant> findByUsernameAndAccountId(String username, Long accountId);
    
    Optional<UserTenant> findByUsernameAndDeletedFalse(String username);
    
    Optional<UserTenant> findByEmailAndDeletedFalse(String email);
    
    List<UserTenant> findByAccountId(Long accountId);
    
    List<UserTenant> findByAccountIdAndDeletedFalse(Long accountId);
    
    Optional<UserTenant> findByEmailAndAccountId(String email, Long accountId);
    
    Optional<UserTenant> findByIdAndAccountId(Long id, Long accountId);
    
    @Query("SELECT COUNT(u) > 0 FROM UserTenant u WHERE u.username = :username AND u.accountId = :accountId AND u.deleted = false")
    boolean existsByUsernameAndAccountId(@Param("username") String username, @Param("accountId") Long accountId);
    
    @Query("SELECT COUNT(u) > 0 FROM UserTenant u WHERE u.email = :email AND u.accountId = :accountId AND u.deleted = false")
    boolean existsByEmailAndAccountId(@Param("email") String email, @Param("accountId") Long accountId);
    
    @Query("SELECT COUNT(u) > 0 FROM UserTenant u WHERE u.email = :email AND u.accountId = :accountId AND u.id != :id AND u.deleted = false")
    boolean existsByEmailAndAccountIdAndIdNot(@Param("email") String email, @Param("accountId") Long accountId, @Param("id") Long id);
    
    @Query("SELECT COUNT(u) > 0 FROM UserTenant u WHERE u.username = :username AND u.accountId = :accountId AND u.id != :id AND u.deleted = false")
    boolean existsByUsernameAndAccountIdAndIdNot(@Param("username") String username, @Param("accountId") Long accountId, @Param("id") Long id);
    
    Optional<UserTenant> findByPasswordResetToken(String token);
}