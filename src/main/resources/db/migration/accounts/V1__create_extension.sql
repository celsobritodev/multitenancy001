-- V1__create_extension.sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";


-- garante case-insensitive no email (idempotente)
CREATE EXTENSION IF NOT EXISTS citext;