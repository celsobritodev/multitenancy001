package brito.com.multitenancy001.controlplane.accounts.app.dto;

import brito.com.multitenancy001.shared.security.TenantRoleName;

public record AccountTenantUserSummaryData(
        Long id,
        Long accountId,
        String name,
        String email,
        TenantRoleName role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}

