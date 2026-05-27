CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    login VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    account_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_account_type CHECK (account_type IN ('USER', 'ADMIN')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'BANNED', 'PENDING'))
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE,
    CONSTRAINT fk_user_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE TABLE admins (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE,
    CONSTRAINT fk_admin_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);
