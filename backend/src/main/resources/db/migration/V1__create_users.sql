CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    roll_number VARCHAR(64),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_roll_number UNIQUE (roll_number),
    CONSTRAINT chk_users_email_normalized CHECK (email = lower(email)),
    CONSTRAINT chk_users_full_name_not_blank CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT chk_users_role CHECK (role IN ('ROLE_ADMIN', 'ROLE_CANDIDATE'))
);

CREATE INDEX idx_users_active_email ON users (email) WHERE is_active = TRUE;
