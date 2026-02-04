package brito.com.multitenancy001.tenant.auth.app.command;

public record TenantLoginConfirmCommand(
        String challengeId,
        Long accountId,
        String slug
) {}
