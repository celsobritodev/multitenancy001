package brito.com.multitenancy001.tenant.auth.app.dto;

/**
 * Conta candidata no login do TENANT (multi-tenant selection).
 * Vem do public.login_identities + public.accounts.
 */
public record TenantLoginCandidateAccount(
        Long accountId,
        String displayName,
        String slug
) {}
