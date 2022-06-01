CREATE TABLE IF NOT EXISTS soknad
(
    uuid        VARCHAR(36)              NOT NULL,
    eier        VARCHAR(20)              NOT NULL,
    soknad_data JSONB                    NOT NULL,
    opprettet   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    PRIMARY KEY (uuid, eier)
);

CREATE TABLE IF NOT EXISTS person_v1
(
    id    BIGSERIAL PRIMARY KEY,
    ident VARCHAR(11) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS soknad_v1
(
    id                 BIGSERIAL PRIMARY KEY,
    uuid               VARCHAR(36) UNIQUE                       NOT NULL,
    person_ident       VARCHAR(11) REFERENCES person_v1 (ident) NOT NULL,
    tilstand           VARCHAR(50)                              NOT NULL,
    dokument_lokasjon  VARCHAR(256)                             NULL,
    journalpost_id     VARCHAR(32) UNIQUE                       NULL,
    opprettet          TIMESTAMP WITH TIME ZONE                 NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    innsendt_tidspunkt TIMESTAMP WITH TIME ZONE                 NULL
);

CREATE TABLE IF NOT EXISTS soknadmal
(
    prosessnavn    VARCHAR(256)             NOT NULL,
    prosessversjon INT                      NOT NULL,
    mal            JSONB                    NOT NULL,
    opprettet      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    PRIMARY KEY (prosessnavn, prosessversjon)
);

CREATE TABLE IF NOT EXISTS aktivitetslogg_v1
(
    id   BIGINT PRIMARY KEY REFERENCES person_v1,
    data JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS soknad_tekst_v1
(
    id    BIGSERIAL PRIMARY KEY,
    uuid  VARCHAR(36) REFERENCES soknad_v1 (uuid) UNIQUE,
    tekst JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS dokument_v1
(
    id                BIGSERIAL PRIMARY KEY,
    soknad_uuid       VARCHAR(36)         NOT NULL REFERENCES soknad_v1 (uuid),
    dokument_lokasjon VARCHAR(256) UNIQUE NOT NULL
);