package brito.com.multitenancy001.controlplane.application.account;

import brito.com.multitenancy001.controlplane.domain.account.TaxIdType;

public record CreateAccountCommand(
        String displayName,
        String loginEmail,
        String taxCountryCode,
        TaxIdType taxIdType,
        String taxIdNumber
) {}
