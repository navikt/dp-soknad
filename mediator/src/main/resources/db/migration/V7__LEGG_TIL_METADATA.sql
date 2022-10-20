CREATE TABLE IF NOT EXISTS metadata
(
    innsending_uuid uuid REFERENCES innsending_v1 (innsending_uuid) ON DELETE CASCADE NOT NULL PRIMARY KEY,
    skjemakode      TEXT DEFAULT NULL,
    tittel          TEXT DEFAULT NULL
);

INSERT INTO metadata (innsending_uuid, skjemakode)
SELECT soknad_uuid, skjemakode
FROM innsending_v1;

ALTER TABLE innsending_v1
    DROP COLUMN skjemakode