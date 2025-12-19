package brito.com.multitenancy001.dtos;

import java.time.LocalDateTime;

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
}
