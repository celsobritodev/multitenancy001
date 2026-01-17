package brito.com.multitenancy001.controlplane.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface ControlPlaneUserRepository extends JpaRepository<ControlPlaneUser, Long> {

	@Query("SELECT u FROM ControlPlaneUser u WHERE u.account.id = :accountId AND u.deleted = false")
	List<ControlPlaneUser> findActiveByAccountId(@Param("accountId") Long accountId);

	
    @Query("SELECT u FROM ControlPlaneUser u WHERE u.id = :id AND u.account.id = :accountId AND u.deleted = false")
    Optional<ControlPlaneUser> findActiveByIdAndAccountId(@Param("id") Long id, @Param("accountId") Long accountId);
    
    
    
    @Query("""
    	    SELECT u
    	      FROM ControlPlaneUser u
    	     WHERE u.account.id = :accountId
    	       AND u.deleted = false
    	       AND lower(u.username) = 'superadmin'
    	""")
    	Optional<ControlPlaneUser> findActiveSuperAdmin(@Param("accountId") Long accountId);


  Optional<ControlPlaneUser> findByUsernameAndAccount_IdAndDeletedFalse(String username, Long accountId);

    long countByAccountIdAndDeletedFalse(Long accountId);

  
    Optional<ControlPlaneUser> findByIdAndAccountId(Long id, Long accountId);

  Optional<ControlPlaneUser> findByUsernameAndDeletedFalse(String username);

  
    // =========================================================
    // ✅ NOVOS: unicidade ativa (deleted=false) e case-insensitive
    // =========================================================

    @Query("""
        SELECT (COUNT(u) > 0)
          FROM ControlPlaneUser u
         WHERE u.account.id = :accountId
           AND u.deleted = false
           AND lower(u.username) = lower(:username)
    """)
    boolean existsActiveByUsernameIgnoreCase(
            @Param("accountId") Long accountId,
            @Param("username") String username
    );

    @Query("""
        SELECT (COUNT(u) > 0)
          FROM ControlPlaneUser u
         WHERE u.account.id = :accountId
           AND u.deleted = false
           AND lower(u.email) = lower(:email)
    """)
    boolean existsActiveByEmailIgnoreCase(
            @Param("accountId") Long accountId,
            @Param("email") String email
    );

    @Query("""
        SELECT (COUNT(u) > 0)
          FROM ControlPlaneUser u
         WHERE u.account.id = :accountId
           AND u.deleted = false
           AND lower(u.username) = lower(:username)
           AND u.id <> :userId
    """)
    boolean existsOtherActiveByUsernameIgnoreCase(
            @Param("accountId") Long accountId,
            @Param("username") String username,
            @Param("userId") Long userId
    );

    @Query("""
        SELECT (COUNT(u) > 0)
          FROM ControlPlaneUser u
         WHERE u.account.id = :accountId
           AND u.deleted = false
           AND lower(u.email) = lower(:email)
           AND u.id <> :userId
    """)
    boolean existsOtherActiveByEmailIgnoreCase(
            @Param("accountId") Long accountId,
            @Param("email") String email,
            @Param("userId") Long userId
    );

    
   

}
