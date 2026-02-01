package brito.com.multitenancy001.controlplane.accounts.api.dto;

import java.time.LocalDateTime;

import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;

public record AccountProvisioningEventResponse(
        Long id,
        Long accountId,
        ProvisioningStatus status,
        ProvisioningFailureCode failureCode,
        String message,
        String detailsJson,
        LocalDateTime createdAt
) {}
