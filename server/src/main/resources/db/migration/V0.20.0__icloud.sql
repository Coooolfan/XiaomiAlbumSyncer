ALTER TABLE xiaomi_account ADD COLUMN provider TEXT NOT NULL DEFAULT 'XIAOMI';
ALTER TABLE album ADD COLUMN cloud_album TEXT;
ALTER TABLE asset ADD COLUMN remote_key TEXT NOT NULL DEFAULT '';
UPDATE asset SET remote_key = CAST(id AS TEXT);
CREATE UNIQUE INDEX idx_asset_remote_key ON asset(album_id, remote_key, sha1);
ALTER TABLE xiaomi_account RENAME TO provider_account;
ALTER TABLE provider_account ADD COLUMN credentials TEXT NOT NULL DEFAULT '{}';
UPDATE provider_account SET credentials = json_object('passToken', pass_token);
ALTER TABLE provider_account DROP COLUMN pass_token;
