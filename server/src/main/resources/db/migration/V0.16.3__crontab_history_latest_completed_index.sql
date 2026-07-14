CREATE INDEX IF NOT EXISTS idx_crontab_history_latest_completed
    ON crontab_history (crontab_id, start_time DESC)
    WHERE end_time IS NOT NULL;
