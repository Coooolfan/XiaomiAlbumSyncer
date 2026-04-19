-- 下载详情的额外消息文本
ALTER TABLE crontab_history_detail
    ADD COLUMN message TEXT DEFAULT NULL;
