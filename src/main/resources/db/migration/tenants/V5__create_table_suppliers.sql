-- V5__create_table_suppliers.sql

CREATE TABLE IF NOT EXISTS suppliers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  name VARCHAR(200) NOT NULL,
  contact_person VARCHAR(100),

  email VARCHAR(150),
  phone VARCHAR(20),
  address TEXT,

  document VARCHAR(20),
  document_type VARCHAR(10),

  website VARCHAR(200),
  payment_terms VARCHAR(100),

  lead_time_days INTEGER,
  rating NUMERIC(3,2),

  active  BOOLEAN NOT NULL DEFAULT true,
  deleted BOOLEAN NOT NULL DEFAULT false,

  notes TEXT,

  -- AUDIT (fonte única)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  created_by_email CITEXT,

  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  updated_by_email CITEXT,

  deleted_at TIMESTAMPTZ,
  deleted_by BIGINT,
  deleted_by_email CITEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_suppliers_document_active
  ON suppliers(document)
  WHERE document IS NOT NULL AND deleted = false;

CREATE INDEX IF NOT EXISTS idx_supplier_name    ON suppliers(name);
CREATE INDEX IF NOT EXISTS idx_supplier_email   ON suppliers(email);
CREATE INDEX IF NOT EXISTS idx_supplier_active  ON suppliers(active);
CREATE INDEX IF NOT EXISTS idx_supplier_deleted ON suppliers(deleted) WHERE deleted = false;

