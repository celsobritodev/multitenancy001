package brito.com.multitenancy001.shared.contracts;

public record AccountRef(
        Long id,
        String tenantSchema,
        String timezone,
        String locale
) {}
