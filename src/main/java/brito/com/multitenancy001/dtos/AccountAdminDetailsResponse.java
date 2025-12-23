package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.entities.account.Account;
import brito.com.multitenancy001.entities.account.UserAccount;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record AccountAdminDetailsResponse(

    // Identificação
    Long id,
    String name,
    String slug,
    String schemaName,
    String status,

    // Dados legais
    String companyDocument,
    String companyEmail,

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
    AdminUserResponse admin,

    // Indicadores
    long totalPlatformUsers,
    boolean active
) {

    // ✅ MÉTODO DE FÁBRICA
    public static AccountAdminDetailsResponse from(
            Account account,
            UserAccount admin
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

                account.getCompanyDocument(),
                account.getCompanyEmail(),

                account.getCreatedAt(),
                account.getTrialEndDate(),
                account.getPaymentDueDate(),
                account.getDeletedAt(),

                inTrial,
                trialExpired,
                trialDaysRemaining,

                AdminUserResponse.from(admin),

                1L, // ⚠️ placeholder (ex: usuários da plataforma)
                account.isActive()
        );
    }
}
