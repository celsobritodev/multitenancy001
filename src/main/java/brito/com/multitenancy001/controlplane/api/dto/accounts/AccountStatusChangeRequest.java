package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;

public record AccountStatusChangeRequest(
        AccountStatus status,
        String reason
) {    }
