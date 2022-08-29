CREATE TABLE IF NOT EXISTS dokumentkrav_filer_v1
(

    faktum_id   VARCHAR                  NOT NULL,
    soknad_uuid VARCHAR(36)              NOT NULL,
    filnavn     VARCHAR                  NOT NULL,
    storrelse   BIGINT                   NOT NULL,
    urn         VARCHAR                  NOT NULL,
    tidspunkt   TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (faktum_id, soknad_uuid) REFERENCES dokumentkrav_v1(faktum_id, soknad_uuid)
);
