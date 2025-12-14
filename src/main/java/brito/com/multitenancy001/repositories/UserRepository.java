package brito.com.multitenancy001.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.entities.master.User;
import brito.com.multitenancy001.entities.master.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {  // ✅ Também Long
    
    
	Optional<User> findByUsernameAndAccountId(String username, Long accountId);

	Optional<User> findByUsernameAndDeletedFalse(String username);
    
    Optional<User> findByEmailAndDeletedFalse(String email);
    
    List<User> findByAccountId(Long accountId);
    
    List<User> findByAccountIdAndDeletedFalse(Long accountId);
    
    List<User> findByAccountIdAndRole(Long accountId, UserRole role);
    
    List<User> findByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);
    
    Optional<User> findByEmailAndAccountId(String email, Long accountId);
    
    Long countByAccountId(Long accountId);
    
    Long countByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);
    
    boolean existsByUsernameAndAccountIdAndIdNot(
    	    String username, Long accountId, Long id
    	);
    
    boolean existsByEmailAndAccountIdAndIdNot(
    	    String email, Long accountId, Long id
    	);
    
    boolean existsByEmailAndAccountId(String email, Long accountId);
    
    @Query("SELECT u FROM User u WHERE u.account.id = :accountId AND u.deleted = false ORDER BY u.name")
    List<User> findActiveUsersByAccount(@Param("accountId") Long accountId);
    
    @Query("SELECT u FROM User u WHERE u.lockedUntil < :now AND u.lockedUntil IS NOT NULL")
    List<User> findUsersWithExpiredLock(@Param("now") LocalDateTime now);
    
    Optional<User> findByIdAndAccountId(Long userId, Long accountId);
    
    Optional<User> findByPasswordResetToken(String token);
    
 // ✅ Adicionar para verificar unicidade por conta
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username AND u.account.id = :accountId AND u.deleted = false")
    boolean existsByUsernameAndAccountId(
            @Param("username") String username,
            @Param("accountId") Long accountId);
    

    
    



    
}