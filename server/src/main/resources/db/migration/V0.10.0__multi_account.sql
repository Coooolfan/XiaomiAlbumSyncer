-- 多账号支持迁移脚本

-- 1. 创建小米账号表
CREATE TABLE xiaomi_account
(
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    nickname   TEXT    NOT NULL,
    pass_token TEXT    NOT NULL,
    user_id    TEXT    NOT NULL
);

-- 2. 迁移现有账号数据（如果存在）
INSERT INTO xiaomi_account (nickname, pass_token, user_id)
SELECT '默认账号', pass_token, user_id
FROM system_config
WHERE id = 0;

INSERT INTO xiaomi_account (nickname, pass_token, user_id)
SELECT '默认账号', '-', '-'
WHERE NOT EXISTS (SELECT 1 FROM xiaomi_account);

-- 3. 重建 album 表（添加 account_id 和 remote_id）
-- SQLite 不支持直接 ADD COLUMN NOT NULL，需要重建表
CREATE TABLE album_new
(
    id               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    remote_id        INTEGER NOT NULL,
    name             TEXT    NOT NULL,
    asset_count      INTEGER NOT NULL,
    last_update_time INTEGER NOT NULL,
    account_id       INTEGER NOT NULL REFERENCES xiaomi_account (id)
);

-- 复制现有数据，假设所有现有数据属于第一个账号
INSERT INTO album_new (remote_id, name, asset_count, last_update_time, account_id)
SELECT id,
       name,
       asset_count,
       last_update_time,
       (SELECT id FROM xiaomi_account LIMIT 1)
FROM album;

-- 创建 id 映射表（用于后续迁移关联表）
CREATE TABLE album_id_mapping
(
    old_id INTEGER NOT NULL,
    new_id INTEGER NOT NULL
);

INSERT INTO album_id_mapping (old_id, new_id)
SELECT remote_id, id
FROM album_new;

-- 更新 asset 表中的 album_id
CREATE TABLE asset_new
(
    id         INTEGER NOT NULL PRIMARY KEY,
    file_name  TEXT    NOT NULL,
    type       TEXT    NOT NULL,
    date_taken INTEGER NOT NULL,
    album_id   INTEGER NOT NULL REFERENCES album_new (id),
    sha1       TEXT    NOT NULL,
    mime_type  TEXT    NOT NULL,
    title      TEXT    NOT NULL,
    size       INTEGER NOT NULL
);

INSERT INTO asset_new (id, file_name, type, date_taken, album_id, sha1, mime_type, title, size)
SELECT a.id,
       a.file_name,
       a.type,
       a.date_taken,
       m.new_id,
       a.sha1,
       a.mime_type,
       a.title,
       a.size
FROM asset a
         JOIN album_id_mapping m ON m.old_id = a.album_id;

-- 更新 crontab_album_mapping 表
DROP TABLE album_id_mapping;

-- 4. 为 crontab 表添加账号关联
CREATE TABLE crontab_new
(
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    config      TEXT    NOT NULL,
    description TEXT    NOT NULL,
    enabled     INTEGER NOT NULL,
    account_id  INTEGER NOT NULL REFERENCES xiaomi_account (id)
);

INSERT INTO crontab_new (id, name, config, description, enabled, account_id)
SELECT id, name, config, description, enabled, (SELECT id FROM xiaomi_account LIMIT 1)
FROM crontab;

CREATE TABLE crontab_album_mapping_new
(
    crontab_id INTEGER NOT NULL REFERENCES crontab_new (id),
    album_id   INTEGER NOT NULL REFERENCES album_new (id),
    PRIMARY KEY (crontab_id, album_id)
);

INSERT INTO crontab_album_mapping_new (crontab_id, album_id)
SELECT cam.crontab_id, m.new_id
FROM crontab_album_mapping cam
         JOIN (SELECT remote_id AS old_id, id AS new_id
               FROM album_new) m ON m.old_id = cam.album_id;

CREATE TABLE crontab_history_new
(
    id                 INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    crontab_id         INTEGER NOT NULL REFERENCES crontab_new,
    start_time         INTEGER NOT NULL,
    end_time           INTEGER,
    timeline_snapshot  TEXT    NOT NULL DEFAULT '{}',
    fetched_all_assets INTEGER NOT NULL DEFAULT 0
);

INSERT INTO crontab_history_new (id, crontab_id, start_time, end_time, timeline_snapshot, fetched_all_assets)
SELECT id, crontab_id, start_time, end_time, timeline_snapshot, fetched_all_assets
FROM crontab_history;

CREATE TABLE crontab_history_detail_new
(
    id                 INTEGER           not null
        primary key autoincrement,
    crontab_history_id INTEGER           not null
        references crontab_history_new,
    asset_id           INTEGER           not null
        references asset_new,
    download_time      INTEGER           not null,
    file_path          TEXT              not null,
    download_completed INTEGER default 0 not null,
    sha1_verified      INTEGER default 0 not null,
    exif_filled        INTEGER default 0 not null,
    fs_time_updated    INTEGER default 0 not null
);

INSERT INTO crontab_history_detail_new (id,
                                        crontab_history_id,
                                        asset_id,
                                        download_time,
                                        file_path,
                                        download_completed,
                                        sha1_verified,
                                        exif_filled,
                                        fs_time_updated)
SELECT id,
       crontab_history_id,
       asset_id,
       download_time,
       file_path,
       download_completed,
       sha1_verified,
       exif_filled,
       fs_time_updated
FROM crontab_history_detail;

DROP TABLE crontab_history_detail;
DROP TABLE crontab_history;
DROP TABLE crontab_album_mapping;
DROP TABLE asset;
DROP TABLE crontab;
DROP TABLE album;

ALTER TABLE album_new
    RENAME TO album;
ALTER TABLE crontab_new
    RENAME TO crontab;
ALTER TABLE asset_new
    RENAME TO asset;
ALTER TABLE crontab_album_mapping_new
    RENAME TO crontab_album_mapping;
ALTER TABLE crontab_history_new
    RENAME TO crontab_history;
ALTER TABLE crontab_history_detail_new
    RENAME TO crontab_history_detail;

-- 5. 从 system_config 移除账号相关字段
-- SQLite 不支持 DROP COLUMN，需要重建表
CREATE TABLE system_config_new
(
    id                        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    password                  TEXT    NOT NULL,
    exif_tool_path            TEXT    NOT NULL,
    assets_date_map_time_zone TEXT    NOT NULL
);

INSERT INTO system_config_new (id, password, exif_tool_path, assets_date_map_time_zone)
SELECT id, password, exif_tool_path, assets_date_map_time_zone
FROM system_config;

DROP TABLE system_config;
ALTER TABLE system_config_new
    RENAME TO system_config;
