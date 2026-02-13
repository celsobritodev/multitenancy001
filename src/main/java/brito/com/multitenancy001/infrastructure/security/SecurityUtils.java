package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

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
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado", 401);
    }

    public Long getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getAccountId();
        }
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado", 401);
    }

    public String getCurrentTenantSchema() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getTenantSchema();
        }
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado", 401);
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
            return ctx.getRoleAuthority();
        }
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado", 401);
    }

    private String extractRoleName(String roleAuthority) {
        if (roleAuthority == null || roleAuthority.isBlank() || !roleAuthority.startsWith("ROLE_")) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "Role inválida no contexto", 403);
        }
        return roleAuthority.substring("ROLE_".length()).trim();
    }

    public TenantRole getCurrentTenantRole() {
        String roleName = extractRoleName(getCurrentRoleAuthority());
        try {
            return TenantRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "Role de tenant não reconhecida: " + roleName, 403);
        }
    }

    public ControlPlaneRole getCurrentControlPlaneRole() {
        String roleName = extractRoleName(getCurrentRoleAuthority());
        try {
            return ControlPlaneRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "Role de control plane não reconhecida: " + roleName, 403);
        }
    }
}
