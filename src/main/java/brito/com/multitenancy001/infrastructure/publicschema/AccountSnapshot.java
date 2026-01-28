package brito.com.multitenancy001.infrastructure.publicschema;

public record AccountSnapshot(
        Long id,
        String schemaName,
        String slug,
        String displayName
) {}
