create table system_config
(
    id        INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    password  TEXT    NOT NULL,
    pass_token TEXT    NOT NULL
);