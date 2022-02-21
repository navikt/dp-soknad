CREATE TABLE IF NOT EXISTS soknad
(
    uuid        VARCHAR(36)                    NOT NULL,
    eier        VARCHAR(20)              NOT NULL,
    soknad_data JSONB                    NOT NULL,
    opprettet   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    PRIMARY KEY (uuid, eier)
);