package brito.com.multitenancy001.shared.account;

public record AccountSnapshot(
        Long id,
        String schemaName,
        String status
) {}
