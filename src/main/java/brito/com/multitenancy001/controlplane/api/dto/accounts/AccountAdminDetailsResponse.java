package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import brito.com.multitenancy001.controlplane.domain.account.TaxIdType;
import java.time.LocalDateTime;

public record AccountAdminDetailsResponse(

    // Identificação
    Long id,
    String name,
    String slug,
    String schemaName,
    String status,

    // Dados legais (sempre em conjunto)
    TaxIdType companyDocType,
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
    long totalControlPlaneUsers,
    boolean active
) {

   
}
