-- V3__create_controlplane_user_permissions.sql
CREATE TABLE IF NOT EXISTS public.controlplane_user_permissions (
    user_id BIGINT NOT NULL,
    permission VARCHAR(120) NOT NULL,

    PRIMARY KEY (user_id, permission),

    CONSTRAINT fk_controlplane_user_permissions_user
        FOREIGN KEY (user_id)
        REFERENCES public.controlplane_users(id)
        ON DELETE CASCADE
);
