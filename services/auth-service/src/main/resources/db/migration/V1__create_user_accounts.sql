CREATE TABLE user_accounts
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    roles         VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

-- Case-insensitive uniqueness: the database, not the application, is the final guard against duplicates.
CREATE UNIQUE INDEX ux_user_accounts_email ON user_accounts (LOWER(email));
