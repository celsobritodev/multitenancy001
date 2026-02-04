package brito.com.multitenancy001.shared.persistence.publicschema;

public record AccountSnapshot(
        Long id,
        String schemaName,
        String slug,
        String displayName
) {}

