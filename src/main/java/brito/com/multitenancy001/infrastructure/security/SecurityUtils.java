package brito.com.multitenancy001.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
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
    
  
    
    public String getCurrentRoleAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext) {
            AuthenticatedUserContext userDetails = (AuthenticatedUserContext) authentication.getPrincipal();
            return userDetails.getRoleAuthority(); // ex: "ROLE_CONTROLPLANE_OWNER"
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }

    /**
     * Lê a role do CONTROLPLANE a partir do roleAuthority armazenado no principal.
     * Ex.: "ROLE_CONTROLPLANE_OWNER" -> ControlPlaneRole.CONTROLPLANE_OWNER
     */
    public ControlPlaneRole getCurrentControlPlaneRole() {
        String roleAuthority = getCurrentRoleAuthority();
        if (roleAuthority == null || !roleAuthority.startsWith("ROLE_")) {
            throw new ApiException("FORBIDDEN", "Role inválida no contexto de autenticação", 403);
        }
        String roleName = roleAuthority.substring("ROLE_".length());
        try {
            return ControlPlaneRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ApiException("FORBIDDEN", "Role não reconhecida: " + roleName, 403);
        }
    }
    
    public String getCurrentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext) {
            AuthenticatedUserContext userDetails = (AuthenticatedUserContext) authentication.getPrincipal();
            return userDetails.getEmail();
        }
        return authentication != null ? authentication.getName() : null;
    }

    
    
}