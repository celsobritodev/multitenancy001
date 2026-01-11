
-- V2__create_tenant_users_permissions.sql
CREATE TABLE IF NOT EXISTS user_tenant_permissions (
    user_tenant_id BIGINT NOT NULL,
    permission VARCHAR(120) NOT NULL,

    PRIMARY KEY (user_tenant_id, permission),

    CONSTRAINT fk_user_tenant_permissions_user
        FOREIGN KEY (user_tenant_id)
        REFERENCES users_tenant(id)
        ON DELETE CASCADE
);