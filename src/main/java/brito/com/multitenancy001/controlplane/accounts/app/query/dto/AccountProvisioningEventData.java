package brito.com.multitenancy001.controlplane.accounts.app.query.dto;

import java.time.Instant;

import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningFailureCode;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;

public record AccountProvisioningEventData(
        Long id,
        Long accountId,
        ProvisioningStatus status,
        ProvisioningFailureCode failureCode,
        String message,
        String detailsJson,
        Instant createdAt
) {}

