-- ================================================================================
-- Migration: V7__create_table_customers.sql
-- Descrição: Cria a tabela de clientes (customers) no schema do tenant.
--            Segue o padrão de audit (AuditInfo) e soft-delete do projeto.
-- ================================================================================

-- Cria a extensão para gerar UUIDs se não existir (idempotente)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS customers (
    -- Identificador único (UUID)
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Campos principais do cliente
    name VARCHAR(200) NOT NULL,
    email CITEXT,                       -- Case-insensitive, único quando não deletado
    phone VARCHAR(20),

    -- Documentos (padrão do projeto)
    document VARCHAR(20),                -- CPF/CNPJ/etc
    document_type VARCHAR(10),           -- 'CPF', 'CNPJ', etc

    -- Endereço
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(20),
    country VARCHAR(60) DEFAULT 'Brasil',

    -- Status e Controle
    active BOOLEAN NOT NULL DEFAULT true,
    deleted BOOLEAN NOT NULL DEFAULT false,

    -- Notas adicionais
    notes TEXT,

    -- ============================================================================
    -- AUDIT (fonte única - AuditInfo)
    -- created_at, created_by, created_by_email: preenchidos no INSERT
    -- updated_at, updated_by, updated_by_email: atualizados no UPDATE
    -- deleted_at, deleted_by, deleted_by_email: preenchidos no soft-delete
    -- ============================================================================
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT,
    created_by_email CITEXT,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_by_email CITEXT,

    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    deleted_by_email CITEXT
);

-- ================================================================================
-- ÍNDICES (otimizados para soft-delete)
-- ================================================================================

-- Unique index para documento ativo (permite múltiplos documentos nulos/deletados)
CREATE UNIQUE INDEX IF NOT EXISTS ux_customers_document_active
    ON customers(document)
    WHERE document IS NOT NULL AND deleted = false;

-- Índices para buscas comuns
CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customers_active ON customers(active);
CREATE INDEX IF NOT EXISTS idx_customers_deleted ON customers(deleted) WHERE deleted = false;

-- Índice para buscas por localização
CREATE INDEX IF NOT EXISTS idx_customers_city ON customers(city);
CREATE INDEX IF NOT EXISTS idx_customers_country ON customers(country);
