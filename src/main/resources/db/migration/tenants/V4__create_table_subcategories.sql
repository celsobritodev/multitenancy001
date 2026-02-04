-- V4__create_table_subcategories.sql

CREATE TABLE IF NOT EXISTS subcategories (
  id BIGSERIAL PRIMARY KEY,

  category_id BIGINT NOT NULL,
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

  CONSTRAINT fk_subcategories_category
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,

  CONSTRAINT uk_subcategories_name_category UNIQUE (category_id, name)
);

CREATE INDEX IF NOT EXISTS idx_subcategories_active  ON subcategories(active);
CREATE INDEX IF NOT EXISTS idx_subcategories_deleted ON subcategories(deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_subcategories_category_id ON subcategories(category_id);

