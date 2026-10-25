ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN google_subject VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT uq_users_google_subject UNIQUE (google_subject),
    ADD CONSTRAINT chk_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'BOTH')),
    ADD CONSTRAINT chk_users_auth_credentials CHECK (
        (auth_provider = 'LOCAL' AND password_hash IS NOT NULL AND google_subject IS NULL)
        OR (auth_provider = 'GOOGLE' AND password_hash IS NULL AND google_subject IS NOT NULL)
        OR (auth_provider = 'BOTH' AND password_hash IS NOT NULL AND google_subject IS NOT NULL)
    );

CREATE INDEX idx_users_google_subject ON users (google_subject) WHERE google_subject IS NOT NULL;
