-- 任务的持久化与并发控制上下文

ALTER TABLE crontab_history_detail
    ADD COLUMN download_completed INTEGER NOT NULL DEFAULT 0;

ALTER TABLE crontab_history_detail
    ADD COLUMN sha1_verified INTEGER NOT NULL DEFAULT 0;

ALTER TABLE crontab_history_detail
    ADD COLUMN exif_filled INTEGER NOT NULL DEFAULT 0;

ALTER TABLE crontab_history_detail
    ADD COLUMN fs_time_updated INTEGER NOT NULL DEFAULT 0;

ALTER TABLE crontab_history
    ADD COLUMN fetched_all_assets INTEGER NOT NULL DEFAULT 0;

-- 假设任务已经全部完成
UPDATE crontab_history_detail
SET download_completed = 1,
    sha1_verified      = 1,
    exif_filled        = 1,
    fs_time_updated    = 1;

UPDATE crontab_history
SET fetched_all_assets = 1;

------ start: 调整 download_time 类型为 INTEGER

-- 1. 创建新表
CREATE TABLE crontab_history_detail_new
(
    id                 INTEGER           not null
        primary key autoincrement,
    crontab_history_id INTEGER           not null
        references crontab_history,
    asset_id           INTEGER           not null
        references asset,
    download_time      INTEGER           not null,
    file_path          TEXT              not null,
    download_completed INTEGER default 0 not null,
    sha1_verified      INTEGER default 0 not null,
    exif_filled        INTEGER default 0 not null,
    fs_time_updated    INTEGER default 0 not null
);

-- 2. 将旧表数据迁移到新表
INSERT INTO crontab_history_detail_new (id,
                                        crontab_history_id,
                                        asset_id,
                                        download_time,
                                        file_path,
                                        download_completed,
                                        sha1_verified,
                                        exif_filled,
                                        fs_time_updated)
SELECT id,
       crontab_history_id,
       asset_id,
       CAST(download_time AS INTEGER), -- 强制类型转换
       file_path,
       download_completed,
       sha1_verified,
       exif_filled,
       fs_time_updated
FROM crontab_history_detail;

-- 3. 删除旧表
DROP TABLE crontab_history_detail;

-- 4. 将新表重命名为原表名
ALTER TABLE crontab_history_detail_new
    RENAME TO "crontab_history_detail";

------ end: 调整 download_time 类型为 INTEGER
