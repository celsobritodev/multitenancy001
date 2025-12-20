package brito.com.multitenancy001.dtos;

import brito.com.multitenancy001.entities.account.AccountStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StatusRequest(
        AccountStatus status,
        String reason
) {

    // Remova qualquer construtor personalizado se existir
    // e deixe apenas o construtor padrão do record
    // Ou use este construtor correto:
    
    @JsonCreator
    public StatusRequest(
            @JsonProperty("status") AccountStatus status,
            @JsonProperty("reason") String reason
    ) {
        this.status = status;
        this.reason = reason;
    }
}