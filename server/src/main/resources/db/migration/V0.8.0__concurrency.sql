-- 任务的持久化与并发控制上下文

ALTER TABLE crontab_history_detail
    ADD COLUMN precheck_completed INTEGER NOT NULL DEFAULT 0;

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
SET precheck_completed = 1,
    download_completed = 1,
    sha1_verified      = 1,
    exif_filled        = 1,
    fs_time_updated    = 1;

UPDATE crontab_history
SET fetched_all_assets = 1;