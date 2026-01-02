-- V5__create_products.sql

-- Se você usa Postgres, isso é opcional (geralmente já existe):
-- CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE products (
  id UUID PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  sku VARCHAR(100) UNIQUE,
  price NUMERIC(10,2) NOT NULL,

  stock_quantity INT NOT NULL DEFAULT 0,
  min_stock INT,
  max_stock INT,

  cost_price NUMERIC(10,2),
  profit_margin NUMERIC(5,2),

  brand VARCHAR(100),
  weight_kg NUMERIC(8,3),
  dimensions VARCHAR(50),
  barcode VARCHAR(50),

  active BOOLEAN NOT NULL DEFAULT true,

  images_json TEXT,
  attributes_json TEXT,

  supplier_id UUID,

  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT false,
  deleted_at TIMESTAMP,

  category_id BIGINT NOT NULL,
  subcategory_id BIGINT NULL,

  CONSTRAINT fk_products_category
    FOREIGN KEY (category_id) REFERENCES categories(id),

  CONSTRAINT fk_products_subcategory_category
    FOREIGN KEY (subcategory_id, category_id)
    REFERENCES subcategories (id, category_id)

  -- Se você tiver tabela suppliers, você pode habilitar isso:
  -- ,CONSTRAINT fk_product_supplier
  --   FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE INDEX idx_product_name ON products(name);
CREATE INDEX idx_product_sku ON products(sku);
CREATE INDEX idx_product_supplier ON products(supplier_id);
CREATE INDEX idx_product_created_at ON products(created_at);
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_subcategory_id ON products(subcategory_id);
