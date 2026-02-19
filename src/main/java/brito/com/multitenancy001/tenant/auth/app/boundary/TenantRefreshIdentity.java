package brito.com.multitenancy001.tenant.auth.app.boundary;

/**
 * Identidade derivada do refresh token do Tenant.
 *
 * Campos:
 * - email: subject do token
 * - accountId: account do token
 * - tenantSchema: schema do token
 * - userId: opcional (pode ser resolvido via query no schema do tenant)
 *
 * Semântica atual do projeto:
 * - resolveRefreshIdentity(refreshToken) NÃO faz query => userId normalmente null
 * - refreshTenantJwt(refreshToken) faz query no tenant => pode preencher userId se quiser
 */
public record TenantRefreshIdentity(
        String email,
        Long accountId,
        String tenantSchema,
        Long userId
) {
    /**
     * Construtor “mínimo” (sem query). Mantém compatibilidade com chamadas:
     * new TenantRefreshIdentity(email, accountId, tenantSchema)
     */
    public TenantRefreshIdentity(String email, Long accountId, String tenantSchema) {
        this(email, accountId, tenantSchema, null);
    }
}
