-- V6__create_table_products.sql
CREATE TABLE IF NOT EXISTS products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  name VARCHAR(200) NOT NULL,
  description TEXT,

  sku VARCHAR(100) NOT NULL,
  price NUMERIC(10,2) NOT NULL,

  stock_quantity INT NOT NULL DEFAULT 0,
  min_stock INT,
  max_stock INT,

  cost_price NUMERIC(10,2),
  profit_margin NUMERIC(5,2),

  category_id BIGINT NOT NULL,
  subcategory_id BIGINT NULL,

  brand VARCHAR(100),
  weight_kg NUMERIC(8,3),
  dimensions VARCHAR(50),
  barcode VARCHAR(50),

  active BOOLEAN NOT NULL DEFAULT true,

  images_json TEXT,
  attributes_json TEXT,

  supplier_id UUID NULL,

  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,

  deleted BOOLEAN NOT NULL DEFAULT false,
  deleted_at TIMESTAMP,

  -- AUDITORIA
  created_by BIGINT,
  updated_by BIGINT,
  deleted_by BIGINT,

  created_by_email VARCHAR(120),
  updated_by_email VARCHAR(120),
  deleted_by_email VARCHAR(120),

  CONSTRAINT fk_products_category
    FOREIGN KEY (category_id) REFERENCES categories(id),

  CONSTRAINT fk_products_subcategory
    FOREIGN KEY (subcategory_id) REFERENCES subcategories(id),

  CONSTRAINT fk_product_supplier
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),

  CONSTRAINT ck_products_sku_not_blank
    CHECK (length(trim(sku)) > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_products_sku_not_deleted
  ON products (sku)
  WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_products_name_lower
  ON products (LOWER(name));

CREATE INDEX IF NOT EXISTS idx_products_brand_lower
  ON products (LOWER(brand));

CREATE INDEX IF NOT EXISTS idx_products_active_deleted
  ON products (active, deleted);

CREATE INDEX IF NOT EXISTS idx_products_supplier_id
  ON products (supplier_id);

CREATE INDEX IF NOT EXISTS idx_products_category_id
  ON products (category_id);

CREATE INDEX IF NOT EXISTS idx_products_subcategory_id
  ON products (subcategory_id);

CREATE INDEX IF NOT EXISTS idx_products_created_at
  ON products (created_at);
