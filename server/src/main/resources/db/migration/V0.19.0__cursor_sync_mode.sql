-- 定时任务新增资产元数据刷新模式：FULL / TIMELINE / CURSOR
ALTER TABLE crontab
    ADD COLUMN sync_mode TEXT NOT NULL DEFAULT 'FULL';

-- 旧配置存于 config JSON：diffByTimeline=true 的任务迁移为 TIMELINE
UPDATE crontab
SET sync_mode = 'TIMELINE'
WHERE json_extract(config, '$.diffByTimeline') = 1;

-- timeline_snapshot 仅用于 TIMELINE 模式；其他模式保持 NULL。
-- SQLite 不支持直接移除 NOT NULL，重建历史表及其明细表以保持外键关系。
CREATE TABLE crontab_history_new
(
    id                   INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    crontab_id           INTEGER NOT NULL REFERENCES crontab (id),
    start_time           INTEGER NOT NULL,
    end_time             INTEGER,
    timeline_snapshot    TEXT,
    album_sync_cursors   TEXT,
    fetched_all_assets   INTEGER NOT NULL DEFAULT 0
);

INSERT INTO crontab_history_new
    (id, crontab_id, start_time, end_time, timeline_snapshot, album_sync_cursors, fetched_all_assets)
SELECT id, crontab_id, start_time, end_time, timeline_snapshot, NULL, fetched_all_assets
FROM crontab_history;

CREATE TABLE crontab_history_detail_new
(
    id                   INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    crontab_history_id   INTEGER NOT NULL REFERENCES crontab_history_new (id),
    asset_id             INTEGER NOT NULL REFERENCES asset (id),
    download_time        INTEGER NOT NULL,
    file_path            TEXT NOT NULL,
    download_completed   INTEGER NOT NULL DEFAULT 0,
    sha1_verified        INTEGER NOT NULL DEFAULT 0,
    exif_filled          INTEGER NOT NULL DEFAULT 0,
    fs_time_updated      INTEGER NOT NULL DEFAULT 0,
    message              TEXT
);

INSERT INTO crontab_history_detail_new
    (id, crontab_history_id, asset_id, download_time, file_path,
     download_completed, sha1_verified, exif_filled, fs_time_updated, message)
SELECT id, crontab_history_id, asset_id, download_time, file_path,
       download_completed, sha1_verified, exif_filled, fs_time_updated, message
FROM crontab_history_detail;

DROP TABLE crontab_history_detail;
DROP TABLE crontab_history;

ALTER TABLE crontab_history_new RENAME TO crontab_history;
ALTER TABLE crontab_history_detail_new RENAME TO crontab_history_detail;

CREATE INDEX idx_crontab_history_crontab_start_time
    ON crontab_history (crontab_id, start_time DESC);
CREATE INDEX idx_crontab_history_detail_history_id
    ON crontab_history_detail (crontab_history_id);
CREATE INDEX idx_crontab_history_detail_asset_history
    ON crontab_history_detail (asset_id, crontab_history_id);

-- targetPath 统一为完整下载路径表达式，并移除不再使用的 expressionTargetPath、diffByTimeline
UPDATE crontab
SET config = json_remove(
    json_set(
        config,
        '$.targetPath',
        CASE
            WHEN trim(coalesce(json_extract(config, '$.expressionTargetPath'), '')) <> '' THEN
                trim(json_extract(config, '$.expressionTargetPath'))
            ELSE rtrim(json_extract(config, '$.targetPath'), '/') || '/' ||
                 '$' || '{album}/' || '$' || '{downloadFileName}'
        END
    ),
    '$.expressionTargetPath',
    '$.diffByTimeline'
);
