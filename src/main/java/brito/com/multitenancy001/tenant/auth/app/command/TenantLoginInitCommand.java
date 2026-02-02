package brito.com.multitenancy001.tenant.auth.app.command;

public record TenantLoginInitCommand(
        String email,
        String password
) { }
