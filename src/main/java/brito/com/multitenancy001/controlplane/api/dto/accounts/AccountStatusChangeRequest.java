package brito.com.multitenancy001.controlplane.api.dto.accounts;

import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AccountStatusChangeRequest(
		@NotNull(message = "status é obrigatório")
        AccountStatus status,
        String reason
) {    }
