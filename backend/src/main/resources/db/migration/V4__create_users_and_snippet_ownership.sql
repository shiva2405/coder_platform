CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(320)  NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    avatar_url      VARCHAR(1000),
    role            VARCHAR(32)   NOT NULL DEFAULT 'USER',
    password_hash   VARCHAR(100),
    github_id       VARCHAR(64),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_users_email ON users (LOWER(email));
CREATE UNIQUE INDEX idx_users_github_id ON users (github_id) WHERE github_id IS NOT NULL;

CREATE TABLE auth_sessions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash      VARCHAR(64)  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_auth_sessions_token_hash ON auth_sessions (token_hash);
CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);

ALTER TABLE snippets
    ADD COLUMN owner_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC';

CREATE INDEX idx_snippets_owner_id ON snippets (owner_id);
CREATE INDEX idx_snippets_visibility ON snippets (visibility);
CREATE INDEX idx_snippets_owner_updated ON snippets (owner_id, updated_at DESC);
