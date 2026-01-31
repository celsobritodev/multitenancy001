package brito.com.multitenancy001.controlplane.signup.api.dto;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;

public record SignupResponse(
        AccountResponse account,
        TenantAdminResponse tenantAdmin
) {}
