ALTER TABLE xiaomi_account ADD COLUMN provider TEXT NOT NULL DEFAULT 'XIAOMI';
ALTER TABLE album ADD COLUMN cloud_album TEXT;
ALTER TABLE asset ADD COLUMN xiaomi_id INTEGER;
ALTER TABLE asset ADD COLUMN remote_key TEXT;
UPDATE asset SET xiaomi_id = id,
    remote_key = 'xiaomi:' || (SELECT account_id FROM album WHERE album.id = asset.album_id) || ':' || id;
ALTER TABLE asset ADD COLUMN cloud_asset TEXT;
CREATE UNIQUE INDEX idx_asset_remote_key ON asset(remote_key) WHERE remote_key IS NOT NULL;
ALTER TABLE xiaomi_account RENAME TO provider_account;
ALTER TABLE provider_account ADD COLUMN credentials TEXT NOT NULL DEFAULT '{}';
UPDATE provider_account SET credentials = json_object('passToken', pass_token);
ALTER TABLE provider_account DROP COLUMN pass_token;
