package brito.com.multitenancy001.shared.contracts;

import brito.com.multitenancy001.shared.security.TenantRoleName;

/**
 * Snapshot compartilhado (contrato) para representar um usuário do Tenant
 * sem expor a entidade do Tenant (bounded context).
 */
public record UserSummaryData(
        Long id,
        Long accountId,
        String name,
        String email,
        TenantRoleName role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted
) {}

