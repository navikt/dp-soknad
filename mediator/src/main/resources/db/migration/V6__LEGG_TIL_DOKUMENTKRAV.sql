CREATE TABLE IF NOT EXISTS sannsynliggjøring_v1
(
    faktumId       VARCHAR     NOT NULL PRIMARY KEY,
    soknad_uuid    VARCHAR(36) NOT NULL REFERENCES soknad_v1 (uuid),
    faktum         JSONB       NOT NULL,
    sannsynliggjør JSONB       NOT NULL
);

CREATE TABLE IF NOT EXISTS dokumentkrav_filer_v1
(
    id                BIGSERIAL PRIMARY KEY,
    filnavn           VARCHAR                  NOT NULL,
    storrelse         INTEGER                  NOT NULL,
    tidspunkt         TIMESTAMP WITH TIME ZONE NOT NULL,
    dokument_lokasjon VARCHAR(256) UNIQUE      NOT NULL
);


CREATE TABLE IF NOT EXISTS dokumentkrav_v1
(

    faktumId      VARCHAR     NOT NULL PRIMARY KEY,
    soknad_uuid   VARCHAR(36) NOT NULL REFERENCES soknad_v1 (uuid),
    beskrivendeId VARCHAR     NOT NULL,
    fakta         JSONB       NOT NULL
);

CREATE TABLE IF NOT EXISTS dokumentkrav_dokumentkravfiler_v1(
    dokmentkrav VARCHAR REFERENCES dokumentkrav_v1(faktumId),
    filer BIGINT REFERENCES dokumentkrav_filer_v1(id),
    UNIQUE (dokmentkrav,filer)
);



