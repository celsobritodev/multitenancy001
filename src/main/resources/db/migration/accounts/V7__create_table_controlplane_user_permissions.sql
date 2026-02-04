-- V7__create_table_controlplane_user_permissions.sql
SET search_path TO public;

CREATE TABLE IF NOT EXISTS controlplane_user_permissions (
    user_id BIGINT NOT NULL,
    permission VARCHAR(120) NOT NULL,

    PRIMARY KEY (user_id, permission),

    CONSTRAINT fk_cp_user_permissions_user
        FOREIGN KEY (user_id)
        REFERENCES controlplane_users(id)
        ON DELETE CASCADE
);

