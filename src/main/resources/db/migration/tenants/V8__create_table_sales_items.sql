-- V8__create_table_sales_items.sql

CREATE TABLE IF NOT EXISTS sale_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  sale_id UUID NOT NULL,
  product_id UUID,
  product_name VARCHAR(255) NOT NULL,

  quantity NUMERIC(12,3) NOT NULL,
  unit_price NUMERIC(12,2) NOT NULL,
  total_price NUMERIC(12,2) NOT NULL,

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

  CONSTRAINT fk_sale_items_sale
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product_id ON sale_items(product_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_deleted ON sale_items(deleted) WHERE deleted = false;

