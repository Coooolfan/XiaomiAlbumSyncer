DROP INDEX IF EXISTS idx_crontab_history_latest_completed;

CREATE INDEX IF NOT EXISTS idx_crontab_history_crontab_start_time
    ON crontab_history (crontab_id, start_time DESC);

CREATE INDEX IF NOT EXISTS idx_asset_album_id
    ON asset (album_id);

CREATE INDEX IF NOT EXISTS idx_asset_date_taken
    ON asset (date_taken);

CREATE INDEX IF NOT EXISTS idx_crontab_history_detail_history_id
    ON crontab_history_detail (crontab_history_id);

CREATE INDEX IF NOT EXISTS idx_crontab_history_detail_asset_history
    ON crontab_history_detail (asset_id, crontab_history_id);

PRAGMA optimize=0x10002;
