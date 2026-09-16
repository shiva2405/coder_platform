ALTER TABLE snippets
    ADD COLUMN files_json TEXT,
    ADD COLUMN entrypoint VARCHAR(255);
