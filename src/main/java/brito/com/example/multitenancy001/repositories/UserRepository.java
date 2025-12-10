package brito.com.example.multitenancy001.repositories;


import brito.com.example.multitenancy001.entities.master.User;
import brito.com.example.multitenancy001.entities.master.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {  // ✅ Também Long
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByUsernameAndDeletedFalse(String username);
    
    Optional<User> findByEmailAndDeletedFalse(String email);
    
    List<User> findByAccountId(Long accountId);
    
    List<User> findByAccountIdAndDeletedFalse(Long accountId);
    
    List<User> findByAccountIdAndRole(Long accountId, UserRole role);
    
    List<User> findByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);
    
    Optional<User> findByEmailAndAccountId(String email, Long accountId);
    
    Long countByAccountId(Long accountId);
    
    Long countByAccountIdAndActiveTrueAndDeletedFalse(Long accountId);
    
    boolean existsByUsernameAndIdNot(String username, Long id);
    
    boolean existsByEmailAndIdNot(String email, Long id);
    
    boolean existsByEmailAndAccountId(String email, Long accountId);
    
    @Query("SELECT u FROM User u WHERE u.account.id = :accountId AND u.deleted = false ORDER BY u.name")
    List<User> findActiveUsersByAccount(@Param("accountId") Long accountId);
    
    @Query("SELECT u FROM User u WHERE u.lockedUntil < :now AND u.lockedUntil IS NOT NULL")
    List<User> findUsersWithExpiredLock(@Param("now") LocalDateTime now);
    
    Optional<User> findByIdAndAccountId(Long userId, Long accountId);
    
    Optional<User> findByPasswordResetToken(String token);

    
    



    
}