package brito.com.multitenancy001.dtos;

import jakarta.validation.constraints.NotNull;
import brito.com.multitenancy001.entities.account.AccountStatus;

public record StatusRequest(
    @NotNull AccountStatus status,
    String reason // opcional (log / auditoria futura)
) {}
