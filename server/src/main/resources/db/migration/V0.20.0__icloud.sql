ALTER TABLE xiaomi_account ADD COLUMN provider TEXT NOT NULL DEFAULT 'XIAOMI';
ALTER TABLE album ADD COLUMN cloud_album TEXT;
ALTER TABLE asset ADD COLUMN xiaomi_id INTEGER;
ALTER TABLE asset ADD COLUMN remote_key TEXT;
UPDATE asset SET xiaomi_id = id,
    remote_key = 'xiaomi:' || (SELECT account_id FROM album WHERE album.id = asset.album_id) || ':' || id;
ALTER TABLE asset ADD COLUMN cloud_asset TEXT;
CREATE UNIQUE INDEX idx_asset_remote_key ON asset(remote_key) WHERE remote_key IS NOT NULL;
CREATE TABLE icloud_session (
    id INTEGER NOT NULL PRIMARY KEY REFERENCES xiaomi_account(id) ON DELETE CASCADE,
    encrypted_data TEXT NOT NULL,
    state TEXT NOT NULL
);
