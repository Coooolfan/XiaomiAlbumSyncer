CREATE TABLE mcp_token
(
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    token_hash TEXT    NOT NULL UNIQUE,
    permission TEXT    NOT NULL CHECK (permission IN ('READ_ONLY', 'ALLOW_TRIGGER')),
    created_at INTEGER NOT NULL
);

-- 旧版只有一枚明文 Token，且没有权限信息。安全起见不继承它，升级后需重新创建。
ALTER TABLE system_config DROP COLUMN mcp_token;
