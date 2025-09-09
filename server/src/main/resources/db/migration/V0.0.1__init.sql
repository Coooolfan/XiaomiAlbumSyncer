create table system_config
(
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    password   TEXT    NOT NULL,
    pass_token TEXT    NOT NULL,
    user_id    TEXT    NOT NULL
);

create table album
(
    id               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    cloud_id         TEXT    NOT NULL UNIQUE,
    name             TEXT    NOT NULL,
    asset_count      INTEGER NOT NULL,
    last_update_time INTEGER NOT NULL
);