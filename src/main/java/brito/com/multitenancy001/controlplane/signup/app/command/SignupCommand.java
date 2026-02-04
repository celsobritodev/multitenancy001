package brito.com.multitenancy001.controlplane.signup.app.command;

import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;

public record SignupCommand(
        String displayName,
        String loginEmail,
        TaxIdType taxIdType,
        String taxIdNumber,
        String password,
        String confirmPassword
) {}

