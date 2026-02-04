package brito.com.multitenancy001.controlplane.accounts.app.command;

import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;

public record CreateAccountCommand(
        String displayName,
        String loginEmail,
        String taxCountryCode,
        TaxIdType taxIdType,
        String taxIdNumber
) {}

