-- V4__create_subcategories.sql

CREATE TABLE IF NOT EXISTS subcategories (
  id BIGSERIAL PRIMARY KEY,

  category_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,

  active  BOOLEAN NOT NULL DEFAULT true,
  deleted BOOLEAN NOT NULL DEFAULT false,
  deleted_at TIMESTAMP,

  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,

  CONSTRAINT fk_subcategories_category
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,

  CONSTRAINT uk_subcategories_name_category UNIQUE (category_id, name)
);


CREATE INDEX IF NOT EXISTS idx_subcategories_active      ON subcategories(active);
CREATE INDEX IF NOT EXISTS idx_subcategories_deleted     ON subcategories(deleted) WHERE deleted = false;
