package brito.com.multitenancy001.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiException;

@Component
@RequiredArgsConstructor
public class SecurityUtils {
    
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext) {
            AuthenticatedUserContext userDetails = (AuthenticatedUserContext) authentication.getPrincipal();
            return userDetails.getUserId();
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }
    
    public Long getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext) {
            AuthenticatedUserContext userDetails = (AuthenticatedUserContext) authentication.getPrincipal();
            return userDetails.getAccountId();
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }
    
    public String getCurrentSchema() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext) {
            AuthenticatedUserContext userDetails = (AuthenticatedUserContext) authentication.getPrincipal();
            return userDetails.getSchemaName();
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }
    
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext) {
            AuthenticatedUserContext userDetails = (AuthenticatedUserContext) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        return authentication != null ? authentication.getName() : null;
    }
}