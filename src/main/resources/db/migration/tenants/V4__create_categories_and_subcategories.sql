-- V4__create_categories_and_subcategories.sql

CREATE TABLE categories (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT false,
  deleted_at TIMESTAMP,

  CONSTRAINT uk_categories_name UNIQUE (name)
);

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

  -- ✅ necessário para criar a FK composta a partir de products
  CONSTRAINT uk_subcategories_id_category UNIQUE (id, category_id)
);

-- Como você vai dropar o banco, pode criar/alterar sem IF NOT EXISTS
ALTER TABLE products
  ADD COLUMN category_id BIGINT NOT NULL,
  ADD COLUMN subcategory_id BIGINT NULL;

-- FK do produto para categoria (normal)
ALTER TABLE products
  ADD CONSTRAINT fk_products_category
    FOREIGN KEY (category_id) REFERENCES categories(id);

-- ✅ FK composta: garante que a subcategoria pertence à mesma categoria do produto
ALTER TABLE products
  ADD CONSTRAINT fk_products_subcategory_category
    FOREIGN KEY (subcategory_id, category_id)
    REFERENCES subcategories (id, category_id);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_subcategory_id ON products(subcategory_id);
