-- 定时任务新增资产元数据刷新模式：FULL / TIMELINE / CURSOR
ALTER TABLE crontab
    ADD COLUMN sync_mode TEXT NOT NULL DEFAULT 'FULL';

-- 旧配置存于 config JSON：diffByTimeline=true 的任务迁移为 TIMELINE
UPDATE crontab
SET sync_mode = 'TIMELINE'
WHERE json_extract(config, '$.diffByTimeline') = 1;

-- CURSOR 模式：各相册的 allitems 拉取位点（albumId -> syncTag 字符串）
ALTER TABLE crontab_history
    ADD COLUMN album_sync_cursors TEXT DEFAULT NULL;

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
