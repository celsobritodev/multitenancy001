package brito.com.multitenancy001.platform.api.dto.accounts;

import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;

public record AccountStatusChangeRequest(
        TenantAccountStatus status,
        String reason
) {    }
