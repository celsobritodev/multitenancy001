package brito.com.multitenancy001.repositories.publicdb;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.platform.domain.user.PlatformUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {
	
	long countByAccountIdAndDeletedFalse(Long accountId);
	
	Optional<PlatformUser> findFirstByAccountIdAndDeletedFalse(Long accountId);
    
    Optional<PlatformUser> findByUsername(String username);
    
    Optional<PlatformUser> findByEmail(String email);
    
    List<PlatformUser> findByAccountId(Long accountId);
    
    @Query("SELECT (COUNT(u) > 0) FROM PlatformUser u " +
    	       "WHERE u.username = :username AND u.account.id = :accountId AND u.deleted = false")
    	boolean existsByUsernameAndAccountId(@Param("username") String username,
    	                                     @Param("accountId") Long accountId);
    
    
    
    @Query("SELECT (COUNT(u) > 0) FROM PlatformUser u " +
    	       "WHERE u.email = :email AND u.account.id = :accountId AND u.deleted = false")
    	boolean existsByEmailAndAccountId(@Param("email") String email,
    	                                  @Param("accountId") Long accountId);    
    Optional<PlatformUser> findByIdAndAccountId(Long id, Long accountId);
    
    Optional<PlatformUser> findByUsernameAndDeletedFalse(String username);
    
    Optional<PlatformUser> findByEmailAndDeletedFalse(String email);
}