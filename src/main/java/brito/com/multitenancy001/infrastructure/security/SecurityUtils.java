package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.security.TenantRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getUserId();
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }

    public Long getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getAccountId();
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }

    public String getCurrentSchema() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getSchemaName();
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }

    public String getCurrentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getEmail();
        }
        return authentication != null ? authentication.getName() : null;
    }

    public String getCurrentRoleAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getRoleAuthority(); // ex: "ROLE_TENANT_OWNER" ou "ROLE_CONTROLPLANE_OWNER"
        }
        throw new ApiException("UNAUTHENTICATED", "Usuário não autenticado", 401);
    }

    /**
     * Extrai o nome da role de um authority no formato "ROLE_XYZ".
     */
    private String extractRoleName(String roleAuthority) {
        if (roleAuthority == null || roleAuthority.isBlank() || !roleAuthority.startsWith("ROLE_")) {
            throw new ApiException("FORBIDDEN", "Role inválida no contexto", 403);
        }
        return roleAuthority.substring("ROLE_".length()).trim();
    }

    /**
     * Role do tenant (ex.: TENANT_OWNER).
     */
    public TenantRole getCurrentTenantRole() {
        String roleName = extractRoleName(getCurrentRoleAuthority());
        try {
            return TenantRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ApiException("FORBIDDEN", "Role de tenant não reconhecida: " + roleName, 403);
        }
    }

    /**
     * Role do control plane (ex.: CONTROLPLANE_OWNER).
     */
    public ControlPlaneRole getCurrentControlPlaneRole() {
        String roleName = extractRoleName(getCurrentRoleAuthority());
        try {
            return ControlPlaneRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ApiException("FORBIDDEN", "Role de control plane não reconhecida: " + roleName, 403);
        }
    }
}
