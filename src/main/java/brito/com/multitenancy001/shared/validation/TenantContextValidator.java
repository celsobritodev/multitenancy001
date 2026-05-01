package brito.com.multitenancy001.shared.validation;

import lombok.extern.slf4j.Slf4j;

/**
 * Validador semântico de contexto tenant/account/user.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Centralizar a obrigatoriedade de accountId, tenantSchema e userId
 *       em fluxos de contexto atual.</li>
 * </ul>
 */
@Slf4j
public final class TenantContextValidator {

    private TenantContextValidator() {
    }

    /**
     * Valida escopo account + tenant.
     *
     * @param accountId conta atual
     * @param tenantSchema schema atual
     */
    public static void requireAccountAndTenant(Long accountId, String tenantSchema) {
        RequiredValidator.requireAccountId(accountId);
        TextValidator.requireTenantSchema(tenantSchema);
    }

    /**
     * Valida escopo account + tenant + user.
     *
     * @param accountId conta atual
     * @param tenantSchema schema atual
     * @param userId usuário atual
     */
    public static void requireAccountTenantAndUser(Long accountId, String tenantSchema, Long userId) {
        requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requireUserId(userId);
    }
}