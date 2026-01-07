package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.DocumentType;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record AccountAdminDetailsResponse(

    // Identificação
    Long id,
    String name,
    String slug,
    String schemaName,
    String status,

    // Dados legais (sempre em conjunto)
    DocumentType companyDocType,
    String companyDocNumber,

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
    long totalPlatformUsers,
    boolean active
) {

    public static AccountAdminDetailsResponse from(
            Account account,
            ControlPlaneUser admin
    ) {

        LocalDateTime now = LocalDateTime.now();

        boolean inTrial = account.getTrialEndDate() != null
                && now.isBefore(account.getTrialEndDate());

        boolean trialExpired = account.getTrialEndDate() != null
                && now.isAfter(account.getTrialEndDate());

        long trialDaysRemaining = 0;
        if (inTrial) {
            trialDaysRemaining = ChronoUnit.DAYS.between(
                    now.toLocalDate(),
                    account.getTrialEndDate().toLocalDate()
            );
        }

        return new AccountAdminDetailsResponse(
                account.getId(),
                account.getName(),
                account.getSlug(),
                account.getSchemaName(),
                account.getStatus().name(),

                account.getCompanyDocType(),
                account.getCompanyDocNumber(),

                account.getCreatedAt(),
                account.getTrialEndDate(),
                account.getPaymentDueDate(),
                account.getDeletedAt(),

                inTrial,
                trialExpired,
                trialDaysRemaining,

                admin != null ? ControlPlaneAdminUserSummaryResponse.from(admin) : null,

                1L, // placeholder
                account.isActive()
        );
    }
}
