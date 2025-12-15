-- ============================
-- TENANT TABLES
-- ============================

-- ============================
-- USERS (DADOS LOCAIS DO TENANT)
-- ============================
CREATE TABLE IF NOT EXISTS tenant_users (
    id BIGSERIAL PRIMARY KEY,

    master_user_id BIGINT NOT NULL, -- referência ao users do public

    name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,

    role VARCHAR(50) NOT NULL,

    active BOOLEAN DEFAULT true,

    last_login TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP,

    CONSTRAINT ux_tenant_user_username UNIQUE (username),
    CONSTRAINT ux_tenant_user_email UNIQUE (email)
);

-- ============================
-- PRODUCTS
-- ============================
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(150) NOT NULL,
    description TEXT,

    sku VARCHAR(100),
    barcode VARCHAR(100),

    price NUMERIC(15,2) NOT NULL,
    cost NUMERIC(15,2),

    stock INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT true,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,

    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_products_sku
    ON products(sku)
    WHERE sku IS NOT NULL;

-- ============================
-- SUPPLIERS
-- ============================
CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(150) NOT NULL,
    document VARCHAR(20),
    email VARCHAR(150),
    phone VARCHAR(20),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deleted BOOLEAN DEFAULT false,
    deleted_at TIMESTAMP
);

-- ============================
-- SALES
-- ============================
CREATE TABLE IF NOT EXISTS sales (
    id BIGSERIAL PRIMARY KEY,

    sale_date TIMESTAMP NOT NULL DEFAULT NOW(),

    total_amount NUMERIC(15,2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',

    created_by BIGINT, -- tenant_user_id

    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================
-- SALE ITEMS
-- ============================
CREATE TABLE IF NOT EXISTS sale_items (
    id BIGSERIAL PRIMARY KEY,

    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    quantity INTEGER NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL,
    total_price NUMERIC(15,2) NOT NULL,

    CONSTRAINT fk_sale_items_sale
        FOREIGN KEY (sale_id)
        REFERENCES sales(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_sale_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
);

-- ============================
-- INDEXES
-- ============================
CREATE INDEX IF NOT EXISTS idx_products_active ON products(active);
CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(sale_date);
