create table system_config
(
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    password   TEXT    NOT NULL,
    pass_token TEXT    NOT NULL,
    user_id    TEXT    NOT NULL
);

create table album
(
    id               INTEGER NOT NULL PRIMARY KEY,
    name             TEXT    NOT NULL,
    asset_count      INTEGER NOT NULL,
    last_update_time INTEGER NOT NULL
);

create table asset
(
    id         INTEGER NOT NULL PRIMARY KEY,
    file_name  TEXT    NOT NULL,
    type       TEXT    NOT NULL,
    date_taken INTEGER NOT NULL,
    album_id   INTEGER NOT NULL,
    sha1       TEXT    NOT NULL,
    mime_type  TEXT    NOT NULL,
    title      TEXT    NOT NULL,
    size       INTEGER NOT NULL,
    FOREIGN KEY (album_id) REFERENCES album (id)
);

create table crontab
(
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    expression  TEXT    NOT NULL,
    time_zone   TEXT    NOT NULL,
    description TEXT    NOT NULL,
    enabled     INTEGER NOT NULL
);

create table crontab_album_mapping
(
    crontab_id INTEGER NOT NULL,
    album_id   INTEGER NOT NULL,
    PRIMARY KEY (crontab_id, album_id),
    FOREIGN KEY (crontab_id) REFERENCES crontab (id),
    FOREIGN KEY (album_id) REFERENCES album (id)
)