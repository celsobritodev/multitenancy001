package brito.com.multitenancy001.repositories;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.entities.account.UserAccount;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	
	long countByAccountIdAndDeletedFalse(Long accountId);
	
	Optional<UserAccount> findFirstByAccountIdAndDeletedFalse(Long accountId);
    
    Optional<UserAccount> findByUsername(String username);
    
    Optional<UserAccount> findByEmail(String email);
    
    List<UserAccount> findByAccountId(Long accountId);
    
    @Query("SELECT COUNT(u) > 0 FROM UserAccount u WHERE u.username = :username AND u.account.id = :accountId AND u.deleted = false")
    boolean existsByUsernameAndAccountId(@Param("username") String username, @Param("accountId") Long accountId);
    
    @Query("SELECT COUNT(u) > 0 FROM UserAccount u WHERE u.email = :email AND u.account.id = :accountId AND u.deleted = false")
    boolean existsByEmailAndAccountId(@Param("email") String email, @Param("accountId") Long accountId);
    
    Optional<UserAccount> findByIdAndAccountId(Long id, Long accountId);
    
    Optional<UserAccount> findByUsernameAndDeletedFalse(String username);
    
    Optional<UserAccount> findByEmailAndDeletedFalse(String email);
}