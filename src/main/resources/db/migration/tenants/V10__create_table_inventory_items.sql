CREATE TABLE inventory_items (
    id BIGSERIAL PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE,
    quantity_available NUMERIC(19, 4) NOT NULL DEFAULT 0,
    quantity_reserved NUMERIC(19, 4) NOT NULL DEFAULT 0,
    min_stock NUMERIC(19, 4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_inventory_items_product_id
    ON inventory_items (product_id);