-- V16__alter_controlplane_users_email_to_citext.sql
SET search_path TO public;


-- email: VARCHAR(150) -> CITEXT (case-insensitive)
ALTER TABLE public.controlplane_users
    ALTER COLUMN email TYPE CITEXT
    USING email::citext;

-- opcional: índice já existe (idx_cp_users_email). Se quiser recriar como citext não precisa.
