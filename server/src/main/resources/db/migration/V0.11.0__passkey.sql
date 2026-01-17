-- Passkey 凭据表
CREATE TABLE passkey_credential
(
    id              TEXT    NOT NULL PRIMARY KEY,  -- credential ID (base64url 编码)
    name            TEXT    NOT NULL,              -- 用户自定义名称，如 "MacBook Touch ID"
    public_key_cose BLOB    NOT NULL,              -- 公钥 (COSE 格式)
    sign_count      INTEGER NOT NULL DEFAULT 0,    -- 签名计数器，用于检测克隆攻击
    transports      TEXT,                          -- 传输方式 JSON 数组，如 ["internal", "hybrid"]
    attestation_fmt TEXT    NOT NULL,              -- 认证格式，如 "packed", "none"
    aaguid          TEXT,                          -- 认证器 AAGUID
    created_at      INTEGER NOT NULL,              -- 创建时间戳
    last_used_at    INTEGER                        -- 最后使用时间戳
);

-- 挑战值临时存储表（用于注册和认证流程）
CREATE TABLE web_authn_challenge
(
    id         TEXT    NOT NULL PRIMARY KEY,  -- 会话标识
    challenge  TEXT    NOT NULL,              -- base64url 编码的挑战值
    type       TEXT    NOT NULL,              -- 'registration' 或 'authentication'
    created_at INTEGER NOT NULL,              -- 创建时间
    expires_at INTEGER NOT NULL               -- 过期时间
);

-- 创建索引加速过期清理查询
CREATE INDEX idx_web_authn_challenge_expires_at ON web_authn_challenge(expires_at);
