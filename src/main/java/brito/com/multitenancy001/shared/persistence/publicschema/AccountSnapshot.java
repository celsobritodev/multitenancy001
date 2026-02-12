package brito.com.multitenancy001.shared.persistence.publicschema;

public record AccountSnapshot(
        Long id,
        String tenantSchema,
        String slug,
        String displayName
) {}
