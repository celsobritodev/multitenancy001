package brito.com.multitenancy001.controlplane.accounts.domain;

public enum ProvisioningFailureCode {
    VALIDATION_ERROR,
    PUBLIC_PERSISTENCE_ERROR,
    SCHEMA_CREATION_ERROR,
    TENANT_MIGRATION_ERROR,
    TENANT_ADMIN_CREATION_ERROR,
    UNKNOWN
}
