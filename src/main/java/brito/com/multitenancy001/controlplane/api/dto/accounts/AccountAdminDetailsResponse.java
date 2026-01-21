package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.domain.account.AccountType;
import brito.com.multitenancy001.controlplane.domain.account.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.domain.account.TaxIdType;

import java.time.LocalDateTime;

public record AccountAdminDetailsResponse(

        // Identificação
        Long id,
        String displayName,
        String slug,
        String schemaName,
        AccountStatus status,
        AccountType accountType,
        SubscriptionPlan subscriptionPlan,

        // Dados legais (sempre em conjunto)
        TaxIdType taxIdType,
        String taxIdNumber,

        // Datas
        LocalDateTime createdAt,
        LocalDateTime trialEndDate,
        LocalDateTime paymentDueDate,
        LocalDateTime deletedAt,

        // Flags calculadas
        boolean inTrial,
        boolean trialExpired,
        long trialDaysRemaining,

        // Admin da conta
        ControlPlaneAdminUserSummaryResponse admin,

        // Indicadores
        long totalControlPlaneUsers,
        boolean operational
) {}
