package brito.com.multitenancy001.shared.persistence.publicschema;

public record PublicAccountView(
        Long id,
        String tenantSchema,
        String slug,
        String displayName
) {}
