-- V8__create_table_sales_items.sql



CREATE TABLE IF NOT EXISTS sale_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  sale_id UUID NOT NULL,
  product_id UUID,
  product_name VARCHAR(255) NOT NULL,

  quantity NUMERIC(12,3) NOT NULL,
  unit_price NUMERIC(12,2) NOT NULL,
  total_price NUMERIC(12,2) NOT NULL,

  -- AUDITORIA (compatível com AuditInfo)
  created_by BIGINT,
  updated_by BIGINT,
  deleted_by BIGINT,

  created_by_username VARCHAR(120),
  updated_by_username VARCHAR(120),
  deleted_by_username VARCHAR(120),

  CONSTRAINT fk_sale_items_sale
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product_id ON sale_items(product_id);
