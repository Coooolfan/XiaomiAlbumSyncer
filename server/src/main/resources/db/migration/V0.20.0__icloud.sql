ALTER TABLE xiaomi_account ADD COLUMN provider TEXT NOT NULL DEFAULT 'XIAOMI';
ALTER TABLE album ADD COLUMN remote_key TEXT NOT NULL DEFAULT '';
UPDATE album SET remote_key = CAST(remote_id AS TEXT);
ALTER TABLE album DROP COLUMN remote_id;
CREATE UNIQUE INDEX idx_album_remote_key ON album(account_id, remote_key);

ALTER TABLE album ADD COLUMN asset_count_nullable INTEGER;
UPDATE album SET asset_count_nullable = asset_count;
ALTER TABLE album DROP COLUMN asset_count;
ALTER TABLE album RENAME COLUMN asset_count_nullable TO asset_count;

ALTER TABLE album ADD COLUMN last_update_time_nullable INTEGER;
UPDATE album SET last_update_time_nullable = last_update_time;
ALTER TABLE album DROP COLUMN last_update_time;
ALTER TABLE album RENAME COLUMN last_update_time_nullable TO last_update_time;

ALTER TABLE asset ADD COLUMN remote_key TEXT NOT NULL DEFAULT '';
UPDATE asset SET remote_key = CAST(id AS TEXT);
ALTER TABLE asset RENAME COLUMN sha1 TO checksum;
CREATE UNIQUE INDEX idx_asset_remote_key ON asset(album_id, remote_key, checksum);
ALTER TABLE xiaomi_account RENAME TO provider_account;
ALTER TABLE provider_account ADD COLUMN credentials TEXT NOT NULL DEFAULT '{}';
UPDATE provider_account SET credentials = json_object('passToken', pass_token);
ALTER TABLE provider_account DROP COLUMN pass_token;
