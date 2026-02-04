package brito.com.multitenancy001.controlplane.signup.app.dto;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;

public record SignupResult(
        Account account,
        TenantAdminResult tenantAdmin
) {}

