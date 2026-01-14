
-- V2__create_table_tenant_users_permissions.sql
CREATE TABLE IF NOT EXISTS tenant_user_permissions (
    tenant_user_id BIGINT NOT NULL,
    permission VARCHAR(120) NOT NULL,

    PRIMARY KEY (tenant_user_id, permission),

    CONSTRAINT fk_tenant_user_permissions_user
        FOREIGN KEY (tenant_user_id)
        REFERENCES tenant_users(id)
        ON DELETE CASCADE
);