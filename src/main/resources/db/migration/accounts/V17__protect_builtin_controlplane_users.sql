-- V17__protect_builtin_controlplane_users.sql
SET search_path TO public;

-- =========================================================
-- 1) Protege controlplane_users (BUILT_IN) contra alterações proibidas
-- =========================================================

CREATE OR REPLACE FUNCTION fn_cp_users_protect_builtin()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- BLOQUEIA DELETE de BUILT_IN
    IF (TG_OP = 'DELETE') THEN
        IF (OLD.user_origin = 'BUILT_IN') THEN
            RAISE EXCEPTION 'BUILT_IN_USER_IMMUTABLE: cannot delete controlplane user id=%', OLD.id
                USING ERRCODE = '42501'; -- insufficient_privilege (semântica: operação proibida)
        END IF;
        RETURN OLD;
    END IF;

    -- BLOQUEIA UPDATE de campos proibidos em BUILT_IN
    IF (TG_OP = 'UPDATE') THEN
        IF (OLD.user_origin = 'BUILT_IN') THEN

            -- Permitimos apenas campos operacionais/segurança e senha:
            -- password, must_change_password, password_changed_at,
            -- last_login, locked_until,
            -- password_reset_token, password_reset_expires,
            -- audit fields (created_*, updated_*, deleted_*)

            IF (NEW.name IS DISTINCT FROM OLD.name)
               OR (NEW.email IS DISTINCT FROM OLD.email)
               OR (NEW.role IS DISTINCT FROM OLD.role)
               OR (NEW.account_id IS DISTINCT FROM OLD.account_id)
               OR (NEW.user_origin IS DISTINCT FROM OLD.user_origin)
               OR (NEW.deleted IS DISTINCT FROM OLD.deleted)
               OR (NEW.suspended_by_account IS DISTINCT FROM OLD.suspended_by_account)
               OR (NEW.suspended_by_admin IS DISTINCT FROM OLD.suspended_by_admin)
            THEN
                RAISE EXCEPTION 'BUILT_IN_USER_IMMUTABLE: forbidden update on BUILT_IN user id=%', OLD.id
                    USING ERRCODE = '42501';
            END IF;
        END IF;

        RETURN NEW;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_cp_users_protect_builtin ON controlplane_users;

CREATE TRIGGER trg_cp_users_protect_builtin
BEFORE UPDATE OR DELETE ON controlplane_users
FOR EACH ROW
EXECUTE FUNCTION fn_cp_users_protect_builtin();


-- =========================================================
-- 2) Protege controlplane_user_permissions para BUILT_IN
-- =========================================================

CREATE OR REPLACE FUNCTION fn_cp_user_permissions_protect_builtin()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_origin VARCHAR(20);
    v_user_id BIGINT;
BEGIN
    IF (TG_OP = 'DELETE') THEN
        v_user_id := OLD.user_id;
    ELSE
        v_user_id := NEW.user_id;
    END IF;

    SELECT u.user_origin INTO v_origin
      FROM controlplane_users u
     WHERE u.id = v_user_id;

    IF (v_origin = 'BUILT_IN') THEN
        RAISE EXCEPTION 'BUILT_IN_USER_IMMUTABLE: cannot change permissions for BUILT_IN user id=%', v_user_id
            USING ERRCODE = '42501';
    END IF;

    IF (TG_OP = 'DELETE') THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_cp_user_permissions_protect_builtin ON controlplane_user_permissions;

CREATE TRIGGER trg_cp_user_permissions_protect_builtin
BEFORE INSERT OR UPDATE OR DELETE ON controlplane_user_permissions
FOR EACH ROW
EXECUTE FUNCTION fn_cp_user_permissions_protect_builtin();
