-- Tabela users COMPLETA (com todas as colunas da entidade User)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100),
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,
    role VARCHAR(50) DEFAULT 'USER',
    account_id BIGINT NOT NULL,
    active BOOLEAN DEFAULT true,
    last_login TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    must_change_password BOOLEAN DEFAULT false,
    password_changed_at TIMESTAMP,
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    timezone VARCHAR(50) DEFAULT 'America/Sao_Paulo',
    locale VARCHAR(10) DEFAULT 'pt_BR',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted BOOLEAN DEFAULT false,
    created_by BIGINT,
    updated_by BIGINT,
    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    UNIQUE(account_id, username),
    UNIQUE(email)
);

-- Tabela de permissões (user_permissions)
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id BIGINT NOT NULL,
    permission VARCHAR(100) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, permission)
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_users_account_id ON users(account_id);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(active) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users(deleted) WHERE deleted = false;