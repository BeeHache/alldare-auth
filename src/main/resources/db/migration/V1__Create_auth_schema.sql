CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    parent_id INTEGER DEFAULT 0,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO roles (name) VALUES
('ADMIN'),
('MOD'),
('USER');

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    account_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    CONSTRAINT chk_account_type CHECK (account_type IN ('USER', 'ADMIN')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'BANNED', 'PENDING'))
);

CREATE UNIQUE INDEX idx_provider_provider_id ON accounts(provider, provider_id) WHERE provider <> 'LOCAL';

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

CREATE TABLE mods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL UNIQUE,
    CONSTRAINT fk_mod_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- Insert default admin account
INSERT INTO accounts (id, login, status, account_type, provider, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin', 'ACTIVE', 'ADMIN', 'LOCAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Link account to ADMIN role
INSERT INTO account_roles (account_id, role_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM roles WHERE name = 'ADMIN';

-- Insert into admins table
INSERT INTO admins (account_id)
VALUES ('00000000-0000-0000-0000-000000000001');