-- V5__create_suppliers.sql

CREATE TABLE IF NOT EXISTS suppliers (
  id UUID PRIMARY KEY,

  name VARCHAR(200) NOT NULL,
  contact_person VARCHAR(100),

  email VARCHAR(150),
  phone VARCHAR(20),
  address TEXT,

  document VARCHAR(20) UNIQUE,
  document_type VARCHAR(10),

  website VARCHAR(200),
  payment_terms VARCHAR(100),

  lead_time_days INTEGER,
  rating NUMERIC(3,2),

  active BOOLEAN NOT NULL DEFAULT true,
  notes TEXT,

  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP,
  deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_supplier_name ON suppliers(name);

CREATE INDEX IF NOT EXISTS idx_supplier_email ON suppliers(email);
