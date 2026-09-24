-- 定时任务新增资产元数据刷新模式：FULL / TIMELINE / CURSOR
ALTER TABLE crontab
    ADD COLUMN sync_mode TEXT NOT NULL DEFAULT 'FULL';

-- 旧配置存于 config JSON：diffByTimeline=true 的任务迁移为 TIMELINE
UPDATE crontab
SET sync_mode = 'TIMELINE'
WHERE json_extract(config, '$.diffByTimeline') = 1;

-- CURSOR 模式：各相册的 allitems 拉取位点（albumId -> {syncTag, incrementalTag}）
ALTER TABLE crontab_history
    ADD COLUMN album_sync_cursors TEXT DEFAULT NULL;

-- targetPath 统一为完整下载路径表达式，不再单独保存 expressionTargetPath
UPDATE crontab
SET config = json_remove(
    json_set(
        config,
        '$.targetPath',
        CASE
            WHEN trim(coalesce(json_extract(config, '$.expressionTargetPath'), '')) <> '' THEN
                CASE
                    WHEN substr(trim(json_extract(config, '$.expressionTargetPath')), 1, 1) = '/'
                        THEN trim(json_extract(config, '$.expressionTargetPath'))
                    ELSE rtrim(json_extract(config, '$.targetPath'), '/') || '/' ||
                         ltrim(trim(json_extract(config, '$.expressionTargetPath')), './')
                END
            ELSE rtrim(json_extract(config, '$.targetPath'), '/') || '/' ||
                 '$' || '{album}/' || '$' || '{downloadFileName}'
        END
    ),
    '$.expressionTargetPath'
);
