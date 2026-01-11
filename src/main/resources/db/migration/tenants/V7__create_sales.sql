-- V7__create_sales.sql

CREATE TABLE IF NOT EXISTS sales (
    id UUID PRIMARY KEY,
    sale_date TIMESTAMP NOT NULL,
    total_amount NUMERIC(10,2),
    customer_name VARCHAR(200),
    customer_email VARCHAR(150),
    status VARCHAR(20)
);

CREATE INDEX IF NOT EXISTS idx_sales_sale_date ON sales (sale_date);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales (status);
CREATE INDEX IF NOT EXISTS idx_sales_customer_email ON sales (customer_email);
