package brito.com.multitenancy001.shared.account;





public record LoginIdentityRow(
        Long accountId,
        String displayName,
        String slug
) {}
