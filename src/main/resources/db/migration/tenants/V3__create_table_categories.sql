-- V3__create_table_categories.sql
CREATE TABLE IF NOT EXISTS categories (
  id BIGSERIAL PRIMARY KEY,

  name VARCHAR(100) NOT NULL,

  active  BOOLEAN NOT NULL DEFAULT true,
  deleted BOOLEAN NOT NULL DEFAULT false,

  -- AUDIT (fonte única)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  created_by_email CITEXT,

  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  updated_by_email CITEXT,

  deleted_at TIMESTAMPTZ,
  deleted_by BIGINT,
  deleted_by_email CITEXT,

  CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_categories_active  ON categories(active);
CREATE INDEX IF NOT EXISTS idx_categories_deleted ON categories(deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_categories_created_at ON categories(created_at);

