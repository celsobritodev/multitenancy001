package brito.com.multitenancy001.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import brito.com.multitenancy001.platform.domain.tenant.TenantAccountStatus;

public record StatusRequest(
        TenantAccountStatus status,
        String reason
) {

    // Remova qualquer construtor personalizado se existir
    // e deixe apenas o construtor padrão do record
    // Ou use este construtor correto:
    
    @JsonCreator
    public StatusRequest(
            @JsonProperty("status") TenantAccountStatus status,
            @JsonProperty("reason") String reason
    ) {
        this.status = status;
        this.reason = reason;
    }
}