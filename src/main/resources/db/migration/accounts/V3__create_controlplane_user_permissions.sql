-- V3__create_controlplane_user_permissions.sql
CREATE TABLE IF NOT EXISTS public.controlplane_user_permissions (
    user_id UUID NOT NULL,
    permission VARCHAR(120) NOT NULL,
    CONSTRAINT pk_cp_user_permissions PRIMARY KEY (user_id, permission),
    CONSTRAINT fk_cp_user_permissions_user
        FOREIGN KEY (user_id) REFERENCES public.controlplane_users(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cp_user_permissions_user_id
    ON public.controlplane_user_permissions(user_id);
