CREATE TABLE inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id UUID NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    reference_type VARCHAR(40),
    reference_id VARCHAR(100),
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_inventory_movements_product_id
    ON inventory_movements (product_id);

CREATE INDEX idx_inventory_movements_created_at
    ON inventory_movements (created_at);

CREATE INDEX idx_inventory_movements_product_created
    ON inventory_movements (product_id, created_at DESC);