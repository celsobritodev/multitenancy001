package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountStatusChangeRequest(
        @NotNull(message = "status é obrigatório")
        AccountStatus status,

        @Size(max = 255, message = "reason deve ter no máximo 255 caracteres")
        String reason
) {}
