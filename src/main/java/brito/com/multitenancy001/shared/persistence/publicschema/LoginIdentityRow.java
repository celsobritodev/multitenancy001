package brito.com.multitenancy001.shared.persistence.publicschema;





public record LoginIdentityRow(
        Long accountId,
        String displayName,
        String slug
) {}

