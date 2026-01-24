package brito.com.multitenancy001.controlplane.api.dto.signup;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;

public record SignupResponse(
        AccountResponse account,
        TenantAdminResponse tenantAdmin
) {}
