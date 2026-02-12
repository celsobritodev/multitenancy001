package brito.com.multitenancy001.shared.contracts;

public record AccountSnapshot(Long id, String tenantSchema, String slug) {}

