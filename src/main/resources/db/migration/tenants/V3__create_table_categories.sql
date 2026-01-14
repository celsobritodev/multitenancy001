-- V3__create_table_categories.sql

CREATE TABLE IF NOT EXISTS categories (
  id BIGSERIAL PRIMARY KEY,

  name VARCHAR(100) NOT NULL,

  active  BOOLEAN NOT NULL DEFAULT true,
  deleted BOOLEAN NOT NULL DEFAULT false,
  deleted_at TIMESTAMP,

  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,

  CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_categories_active  ON categories(active);
CREATE INDEX IF NOT EXISTS idx_categories_deleted ON categories(deleted) WHERE deleted = false;
