ALTER TABLE crontab_history
    ADD COLUMN timeline_snapshot TEXT NOT NULL DEFAULT '{}';