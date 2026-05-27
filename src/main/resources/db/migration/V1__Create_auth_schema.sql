CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    parent_id INTEGER DEFAULT 0,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO roles (id, name) VALUES 
(1, 'ADMIN'),
(2, 'MOD'),
(3, 'USER');

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    account_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_account_type CHECK (account_type IN ('USER', 'ADMIN')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'BANNED', 'PENDING'))
);

CREATE TABLE account_roles (
    account_id UUID NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (account_id, role_id),
    CONSTRAINT fk_ar_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE,
    CONSTRAINT fk_user_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE TABLE admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE,
    CONSTRAINT fk_admin_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);