package brito.com.multitenancy001.integration.auth;

/**
 * Identidade derivada do refresh token do ControlPlane.
 */
public record ControlPlaneRefreshIdentity(
        String email,
        Long accountId,
        String schema
) {}
