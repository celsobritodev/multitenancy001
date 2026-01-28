package brito.com.multitenancy001.infrastructure.publicschema;





public record LoginIdentityRow(
        Long accountId,
        String displayName,
        String slug
) {}
