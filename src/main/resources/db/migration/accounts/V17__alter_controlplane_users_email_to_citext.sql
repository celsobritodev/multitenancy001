-- V17__alter_controlplane_users_email_to_citext.sql
SET search_path TO public;

-- CITEXT já é criado no V1 (accounts), mas manter idempotente não faz mal
CREATE EXTENSION IF NOT EXISTS citext;

-- email: VARCHAR(150) -> CITEXT (case-insensitive)
ALTER TABLE public.controlplane_users
    ALTER COLUMN email TYPE CITEXT
    USING email::citext;

-- opcional: índice já existe (idx_cp_users_email). Se quiser recriar como citext não precisa.
