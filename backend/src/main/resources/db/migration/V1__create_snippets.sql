CREATE TABLE snippets (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(16)  NOT NULL,
    language        VARCHAR(32)  NOT NULL,
    code            TEXT         NOT NULL,
    stdin           TEXT         NOT NULL DEFAULT '',
    title           VARCHAR(200),
    view_count      BIGINT       NOT NULL DEFAULT 0,
    forked_from_slug VARCHAR(16),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_snippets_slug ON snippets (slug);
CREATE INDEX idx_snippets_created_at ON snippets (created_at DESC);
