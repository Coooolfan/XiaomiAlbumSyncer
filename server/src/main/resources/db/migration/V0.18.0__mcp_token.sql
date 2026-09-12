CREATE TABLE mcp_token
(
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    token_hash TEXT    NOT NULL UNIQUE,
    permission TEXT    NOT NULL CHECK (permission IN ('READ_ONLY', 'ALLOW_TRIGGER')),
    created_at INTEGER NOT NULL
);
