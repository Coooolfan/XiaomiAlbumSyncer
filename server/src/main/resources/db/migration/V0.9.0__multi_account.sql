-- 多账号支持迁移脚本

-- 1. 创建小米账号表
CREATE TABLE xiaomi_account (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    nickname TEXT NOT NULL,
    pass_token TEXT NOT NULL,
    user_id TEXT NOT NULL
);

-- 2. 迁移现有账号数据（如果存在）
INSERT INTO xiaomi_account (nickname, pass_token, user_id)
SELECT '默认账号', pass_token, user_id FROM system_config WHERE id = 0 AND pass_token != '-';

-- 3. 重建 album 表（添加 account_id 和 remote_id）
-- SQLite 不支持直接 ADD COLUMN NOT NULL，需要重建表
CREATE TABLE album_new (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    remote_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    asset_count INTEGER NOT NULL,
    last_update_time INTEGER NOT NULL,
    account_id INTEGER NOT NULL REFERENCES xiaomi_account(id)
);

-- 复制现有数据，假设所有现有数据属于第一个账号
INSERT INTO album_new (remote_id, name, asset_count, last_update_time, account_id)
SELECT id, name, asset_count, last_update_time,
       COALESCE((SELECT id FROM xiaomi_account LIMIT 1), 1)
FROM album;

-- 创建 id 映射表（用于后续迁移关联表）
CREATE TABLE album_id_mapping (
    old_id INTEGER NOT NULL,
    new_id INTEGER NOT NULL
);

INSERT INTO album_id_mapping (old_id, new_id)
SELECT a.id, an.id
FROM album a
JOIN album_new an ON a.id = an.remote_id
                  AND an.account_id = COALESCE((SELECT id FROM xiaomi_account LIMIT 1), 1);

-- 更新 asset 表中的 album_id
UPDATE asset SET album_id = (
    SELECT new_id FROM album_id_mapping WHERE old_id = asset.album_id
) WHERE album_id IN (SELECT old_id FROM album_id_mapping);

-- 更新 crontab_album_mapping 表
UPDATE crontab_album_mapping SET album_id = (
    SELECT new_id FROM album_id_mapping WHERE old_id = crontab_album_mapping.album_id
) WHERE album_id IN (SELECT old_id FROM album_id_mapping);

DROP TABLE album;
ALTER TABLE album_new RENAME TO album;
DROP TABLE album_id_mapping;

-- 4. 为 crontab 表添加账号关联
ALTER TABLE crontab ADD COLUMN account_id INTEGER REFERENCES xiaomi_account(id);
-- 关联现有数据到第一个账号
UPDATE crontab SET account_id = (SELECT id FROM xiaomi_account LIMIT 1) WHERE account_id IS NULL;

-- 5. 从 system_config 移除账号相关字段
-- SQLite 不支持 DROP COLUMN，需要重建表
CREATE TABLE system_config_new (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    password TEXT NOT NULL,
    exif_tool_path TEXT NOT NULL,
    assets_date_map_time_zone TEXT NOT NULL
);

INSERT INTO system_config_new (id, password, exif_tool_path, assets_date_map_time_zone)
SELECT id, password, exif_tool_path, assets_date_map_time_zone FROM system_config;

DROP TABLE system_config;
ALTER TABLE system_config_new RENAME TO system_config;
