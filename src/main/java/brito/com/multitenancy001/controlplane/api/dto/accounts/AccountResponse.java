package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        String displayName,
        String slug,
        String schemaName,
        String status,
        String accountType,
        String subscriptionPlan,
        LocalDateTime createdAt,
        LocalDateTime trialEndDate,
        ControlPlaneAdminUserSummaryResponse platformAdmin
) {}
