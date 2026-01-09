package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneAdminUserSummaryResponse;
import java.time.LocalDateTime;

public record AccountResponse(
    Long id,
    String name,
    String slug,
    String schemaName,
    String status,
    LocalDateTime createdAt,
    LocalDateTime trialEndDate,
    ControlPlaneAdminUserSummaryResponse admin,
    boolean systemAccount
) {
    
   
  
    
  
}