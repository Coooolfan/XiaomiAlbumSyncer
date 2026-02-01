-- 同步和归档功能数据库迁移脚本

-- ========== 同步记录表 ==========

-- 同步记录主表
CREATE TABLE sync_record
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    crontab_id    INTEGER NOT NULL,                    -- 关联的定时任务 ID
    sync_time     INTEGER NOT NULL,                    -- 同步时间（Unix 时间戳，毫秒）
    added_count   INTEGER NOT NULL DEFAULT 0,          -- 新增文件数
    deleted_count INTEGER NOT NULL DEFAULT 0,          -- 删除文件数
    updated_count INTEGER NOT NULL DEFAULT 0,          -- 更新文件数
    status        TEXT    NOT NULL DEFAULT 'RUNNING',  -- 同步状态：RUNNING, COMPLETED, FAILED
    error_message TEXT,                                -- 错误信息
    FOREIGN KEY (crontab_id) REFERENCES crontab (id) ON DELETE CASCADE
);

-- 同步记录详情表
CREATE TABLE sync_record_detail
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    sync_record_id INTEGER NOT NULL,                   -- 关联的同步记录 ID
    asset_id       INTEGER,                            -- 关联的资产 ID（删除操作时可能为 NULL）
    operation      TEXT    NOT NULL,                   -- 操作类型：ADD, DELETE, UPDATE
    file_path      TEXT    NOT NULL,                   -- 文件路径
    is_completed   INTEGER NOT NULL DEFAULT 0,         -- 是否完成（0: 否, 1: 是）
    error_message  TEXT,                               -- 错误信息
    FOREIGN KEY (sync_record_id) REFERENCES sync_record (id) ON DELETE CASCADE,
    FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE SET NULL
);

-- ========== 归档记录表 ==========

-- 归档记录主表
CREATE TABLE archive_record
(
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    crontab_id          INTEGER NOT NULL,                       -- 关联的定时任务 ID
    archive_time        INTEGER NOT NULL,                       -- 归档时间（Unix 时间戳，毫秒）
    archive_mode        TEXT    NOT NULL,                       -- 归档模式：TIME, SPACE
    archive_before_date TEXT    NOT NULL,                       -- 归档此日期之前的照片（ISO 8601 格式：YYYY-MM-DD）
    archived_count      INTEGER NOT NULL DEFAULT 0,             -- 归档文件数
    freed_space_bytes   INTEGER NOT NULL DEFAULT 0,             -- 释放的云空间（字节）
    status              TEXT    NOT NULL DEFAULT 'PLANNING',    -- 归档状态：PLANNING, MOVING_FILES, DELETING_CLOUD, COMPLETED, FAILED
    error_message       TEXT,                                   -- 错误信息
    FOREIGN KEY (crontab_id) REFERENCES crontab (id) ON DELETE CASCADE
);

-- 归档详情表
CREATE TABLE archive_detail
(
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    archive_record_id       INTEGER NOT NULL,                   -- 关联的归档记录 ID
    asset_id                INTEGER NOT NULL,                   -- 关联的资产 ID
    source_path             TEXT    NOT NULL,                   -- 原路径（sync）
    target_path             TEXT    NOT NULL,                   -- 目标路径（backup）
    is_moved_to_backup      INTEGER NOT NULL DEFAULT 0,         -- 是否已移动到 backup（0: 否, 1: 是）
    is_deleted_from_cloud   INTEGER NOT NULL DEFAULT 0,         -- 是否已从云端删除（0: 否, 1: 是）
    error_message           TEXT,                               -- 错误信息
    FOREIGN KEY (archive_record_id) REFERENCES archive_record (id) ON DELETE CASCADE,
    FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE CASCADE
);

-- ========== 索引 ==========

-- 同步记录索引
CREATE INDEX idx_sync_record_crontab_id ON sync_record (crontab_id);
CREATE INDEX idx_sync_record_sync_time ON sync_record (sync_time DESC);
CREATE INDEX idx_sync_record_status ON sync_record (status);

-- 同步记录详情索引
CREATE INDEX idx_sync_record_detail_sync_record_id ON sync_record_detail (sync_record_id);
CREATE INDEX idx_sync_record_detail_asset_id ON sync_record_detail (asset_id);
CREATE INDEX idx_sync_record_detail_operation ON sync_record_detail (operation);

-- 归档记录索引
CREATE INDEX idx_archive_record_crontab_id ON archive_record (crontab_id);
CREATE INDEX idx_archive_record_archive_time ON archive_record (archive_time DESC);
CREATE INDEX idx_archive_record_status ON archive_record (status);
CREATE INDEX idx_archive_record_archive_mode ON archive_record (archive_mode);

-- 归档详情索引
CREATE INDEX idx_archive_detail_archive_record_id ON archive_detail (archive_record_id);
CREATE INDEX idx_archive_detail_asset_id ON archive_detail (asset_id);
CREATE INDEX idx_archive_detail_is_moved_to_backup ON archive_detail (is_moved_to_backup);
CREATE INDEX idx_archive_detail_is_deleted_from_cloud ON archive_detail (is_deleted_from_cloud);
