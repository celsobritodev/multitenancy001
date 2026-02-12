package brito.com.multitenancy001.controlplane.accounts.api.dto;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountType;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.domain.TaxIdType;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneAdminUserSummaryResponse;

import java.time.Instant;
import java.time.LocalDate;

public record AccountAdminDetailsResponse(

        // Identificação
        Long id,
        String displayName,
        String slug,
        String tenantSchema,
        AccountStatus status,
        AccountType accountType,
        SubscriptionPlan subscriptionPlan,

        // Dados legais (sempre em conjunto)
        TaxIdType taxIdType,
        String taxIdNumber,

        // Datas
        Instant createdAt,
        Instant trialEndAt,
        LocalDate paymentDueDate,
        Instant deletedAt,

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
