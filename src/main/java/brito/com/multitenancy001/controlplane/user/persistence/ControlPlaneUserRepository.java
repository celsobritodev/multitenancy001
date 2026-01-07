package brito.com.multitenancy001.controlplane.user.persistence;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControlPlaneUserRepository extends JpaRepository<ControlPlaneUser, Long> {
	
	long countByAccountIdAndDeletedFalse(Long accountId);
	
	Optional<ControlPlaneUser> findFirstByAccountIdAndDeletedFalse(Long accountId);
    
    Optional<ControlPlaneUser> findByUsername(String username);
    
    Optional<ControlPlaneUser> findByEmail(String email);
    
    List<ControlPlaneUser> findByAccountId(Long accountId);
    
    @Query("SELECT (COUNT(u) > 0) FROM PlatformUser u " +
    	       "WHERE u.username = :username AND u.account.id = :accountId AND u.deleted = false")
    	boolean existsByUsernameAndAccountId(@Param("username") String username,
    	                                     @Param("accountId") Long accountId);
    
    
    
    @Query("SELECT (COUNT(u) > 0) FROM PlatformUser u " +
    	       "WHERE u.email = :email AND u.account.id = :accountId AND u.deleted = false")
    	boolean existsByEmailAndAccountId(@Param("email") String email,
    	                                  @Param("accountId") Long accountId);    
    Optional<ControlPlaneUser> findByIdAndAccountId(Long id, Long accountId);
    
    Optional<ControlPlaneUser> findByUsernameAndDeletedFalse(String username);
    
    Optional<ControlPlaneUser> findByEmailAndDeletedFalse(String email);
}