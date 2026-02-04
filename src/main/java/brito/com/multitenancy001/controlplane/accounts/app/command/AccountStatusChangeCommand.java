package brito.com.multitenancy001.controlplane.accounts.app.command;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;

public record AccountStatusChangeCommand(
        AccountStatus status
) {}

