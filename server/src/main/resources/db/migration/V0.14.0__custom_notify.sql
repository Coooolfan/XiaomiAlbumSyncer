-- 自定义通知配置
ALTER TABLE system_config
    ADD COLUMN notify_config TEXT NOT NULL DEFAULT '{"url":"","headers":{},"body":""}';

-- 仅迁移已配置 ftqq_key 的记录
UPDATE system_config
SET notify_config = json_object(
    'url',
    CASE
        -- 同时兼容 Server 酱 Turbo 与 Server 酱 3
        WHEN trim(ftqq_key) LIKE 'sctp%'
            THEN 'https://'
                || CASE
                       -- 等价于 Kotlin: ftqqKey.substring(4).substringBefore("t")
                       WHEN instr(substr(trim(ftqq_key), 5), 't') > 0
                           THEN substr(substr(trim(ftqq_key), 5), 1, instr(substr(trim(ftqq_key), 5), 't') - 1)
                       ELSE substr(trim(ftqq_key), 5)
                   END
                || '.push.ft07.com/send/'
                || trim(ftqq_key)
                || '.send'
        ELSE 'https://sctapi.ftqq.com/' || trim(ftqq_key) || '.send'
    END,
    'headers', json('{"Content-Type":"application/json"}'),
    'body',
    '{"text":"Xiaomi Album Syncer","desp":"定时任务 '
        || char(36) || '{crontab.name} 已完成同步，成功 '
        || char(36) || '{success}/'
        || char(36) || '{total}"}'
)
WHERE length(trim(ftqq_key)) > 0;

ALTER TABLE system_config
    DROP COLUMN ftqq_key;
