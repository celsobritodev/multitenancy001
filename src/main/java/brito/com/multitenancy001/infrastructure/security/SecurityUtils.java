package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.security.TenantRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utilitário de acesso ao contexto de segurança da aplicação.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Extrair dados do usuário autenticado a partir do {@link SecurityContextHolder}.</li>
 *   <li>Fornecer acesso seguro a informações como userId, accountId, tenantSchema e role.</li>
 *   <li>Padronizar falhas de autenticação e autorização.</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Se não houver usuário autenticado, lança {@link ApiException} com {@link ApiErrorCode#UNAUTHENTICATED}.</li>
 *   <li>Validação de roles inválidas resulta em {@link ApiErrorCode#FORBIDDEN}.</li>
 * </ul>
 */
@Component
public class SecurityUtils {

    /**
     * Obtém o ID do usuário autenticado.
     *
     * @return userId
     * @throws ApiException se não houver autenticação válida
     */
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getUserId();
        }
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado");
    }

    /**
     * Obtém o ID da account do usuário autenticado.
     *
     * @return accountId
     * @throws ApiException se não houver autenticação válida
     */
    public Long getCurrentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getAccountId();
        }
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado");
    }

    /**
     * Obtém o schema do tenant atual.
     *
     * @return tenantSchema
     * @throws ApiException se não houver autenticação válida
     */
    public String getCurrentTenantSchema() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getTenantSchema();
        }
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado");
    }

    /**
     * Obtém o email do usuário autenticado.
     *
     * @return email ou null se não disponível
     */
    public String getCurrentEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getEmail();
        }
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Obtém a authority (ROLE_*) do usuário autenticado.
     *
     * @return role authority
     * @throws ApiException se não houver autenticação válida
     */
    public String getCurrentRoleAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserContext ctx) {
            return ctx.getRoleAuthority();
        }
        throw new ApiException(ApiErrorCode.UNAUTHENTICATED, "Usuário não autenticado");
    }

    /**
     * Verifica se há usuário autenticado no contexto.
     *
     * @return true se autenticado
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * Alias para compatibilidade.
     */
    public Long getAuthenticatedUserId() {
        return getCurrentUserId();
    }

    /**
     * Alias para compatibilidade.
     */
    public String getAuthenticatedEmail() {
        return getCurrentEmail();
    }

    /**
     * Extrai o nome da role a partir da authority.
     *
     * @param roleAuthority authority no formato ROLE_*
     * @return nome da role
     */
    private String extractRoleName(String roleAuthority) {
        if (roleAuthority == null || roleAuthority.isBlank() || !roleAuthority.startsWith("ROLE_")) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "Role inválida no contexto");
        }
        return roleAuthority.substring("ROLE_".length()).trim();
    }

    /**
     * Obtém o papel do usuário no contexto tenant.
     *
     * @return TenantRole
     * @throws ApiException se role inválida
     */
    public TenantRole getCurrentTenantRole() {
        String roleName = extractRoleName(getCurrentRoleAuthority());
        try {
            return TenantRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "Role de tenant não reconhecida: " + roleName);
        }
    }
}