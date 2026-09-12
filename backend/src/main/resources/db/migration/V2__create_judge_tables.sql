CREATE TABLE problems (
    id                  BIGSERIAL PRIMARY KEY,
    slug                VARCHAR(64)   NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         TEXT          NOT NULL,
    difficulty          VARCHAR(16)   NOT NULL,
    tags                VARCHAR(500)  NOT NULL DEFAULT '',
    time_limit_ms       INTEGER       NOT NULL,
    memory_limit_bytes  BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_problems_slug ON problems (slug);
CREATE INDEX idx_problems_difficulty ON problems (difficulty);

CREATE TABLE test_cases (
    id               BIGSERIAL PRIMARY KEY,
    problem_id       BIGINT       NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    input_data       TEXT         NOT NULL DEFAULT '',
    expected_output  TEXT         NOT NULL DEFAULT '',
    points           INTEGER      NOT NULL DEFAULT 0,
    is_sample        BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order       INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_test_cases_problem ON test_cases (problem_id, sort_order, id);

CREATE TABLE submissions (
    id             BIGSERIAL PRIMARY KEY,
    problem_id     BIGINT       NOT NULL REFERENCES problems (id) ON DELETE CASCADE,
    language       VARCHAR(32)  NOT NULL,
    code           TEXT         NOT NULL,
    verdict        VARCHAR(32)  NOT NULL,
    runtime_ms     BIGINT       NOT NULL DEFAULT 0,
    passed_count   INTEGER      NOT NULL DEFAULT 0,
    total_count    INTEGER      NOT NULL DEFAULT 0,
    score          INTEGER      NOT NULL DEFAULT 0,
    max_score      INTEGER      NOT NULL DEFAULT 0,
    compile_error  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_submissions_problem_created ON submissions (problem_id, created_at DESC);

CREATE TABLE submission_case_results (
    id                BIGSERIAL PRIMARY KEY,
    submission_id     BIGINT       NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    test_case_id      BIGINT       REFERENCES test_cases (id) ON DELETE SET NULL,
    is_sample         BOOLEAN      NOT NULL DEFAULT FALSE,
    verdict           VARCHAR(32)  NOT NULL,
    runtime_ms        BIGINT       NOT NULL DEFAULT 0,
    points            INTEGER      NOT NULL DEFAULT 0,
    input_data        TEXT,
    expected_output   TEXT,
    actual_output     TEXT,
    error_output      TEXT,
    sort_order        INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_submission_case_results_submission ON submission_case_results (submission_id, sort_order);
