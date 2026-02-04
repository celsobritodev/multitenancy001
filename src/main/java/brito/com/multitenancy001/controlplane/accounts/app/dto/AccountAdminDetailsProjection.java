package brito.com.multitenancy001.controlplane.accounts.app.dto;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;

public record AccountAdminDetailsProjection(
        Account account,
        ControlPlaneUser admin,
        long totalUsers
) {}

