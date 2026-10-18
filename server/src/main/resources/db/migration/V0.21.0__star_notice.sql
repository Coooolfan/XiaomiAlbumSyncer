-- Star 提示卡片
ALTER TABLE system_config
    ADD COLUMN star_notice_dismissed BOOLEAN NOT NULL DEFAULT FALSE;
