package brito.com.multitenancy001.shared.contracts;

public record AccountRef(
        Long id,
        String schemaName,
        String timezone,
        String locale
) {}
