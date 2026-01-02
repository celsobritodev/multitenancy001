-- V4__create_subcategories.sql
CREATE TABLE subcategories (
  id BIGSERIAL PRIMARY KEY,
  category_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT false,
  deleted_at TIMESTAMP,

  CONSTRAINT fk_subcategories_category
    FOREIGN KEY (category_id) REFERENCES categories(id),

  CONSTRAINT uk_subcategories_name_category UNIQUE (category_id, name),

  -- necessário pra FK composta do products
  CONSTRAINT uk_subcategories_id_category UNIQUE (id, category_id)
);

CREATE INDEX idx_subcategories_category_id ON subcategories(category_id);
