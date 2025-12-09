-- Banco master
CREATE DATABASE master_db;

\c master_db;

CREATE TABLE accounts (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    schema_name VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATE NOT NULL,
    trial_end_date DATE,
    payment_due_date DATE
);

CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE TABLE payments (
    id VARCHAR(255) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    amount NUMERIC(10,2),
    payment_date TIMESTAMP,
    valid_until TIMESTAMP,
    status VARCHAR(50),
    transaction_id VARCHAR(255),
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);