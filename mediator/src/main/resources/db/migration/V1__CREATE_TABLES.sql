CREATE TABLE IF NOT EXISTS person_v1
(
    id    bigserial PRIMARY KEY,
    ident varchar(11) NOT NULL UNIQUE
);


CREATE TABLE IF NOT EXISTS soknad_v1
(
    id                    bigserial PRIMARY KEY,
    uuid                  varchar(36)                                                       NOT NULL UNIQUE,
    person_ident          varchar(11)                                                       NOT NULL REFERENCES person_v1 (ident),
    tilstand              varchar(50)                                                       NOT NULL,
    dokument_lokasjon     varchar(256),
    journalpost_id        varchar(32) UNIQUE,
    opprettet             timestamp WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::text) NOT NULL,
    innsendt_tidspunkt    timestamp WITH TIME ZONE,
    spraak                text                     DEFAULT 'NB'::text                       NOT NULL,
    sist_endret_av_bruker timestamp WITH TIME ZONE
);



CREATE TABLE IF NOT EXISTS soknad_cache
(
    uuid        varchar(36)                                                       NOT NULL
        REFERENCES soknad_v1 (uuid)
            ON DELETE CASCADE,
    eier        varchar(20)                                                       NOT NULL,
    soknad_data jsonb                                                             NOT NULL,
    mottatt     timestamp WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::text) NOT NULL,
    sist_endret timestamp                DEFAULT NOW()                            NOT NULL,
    PRIMARY KEY (uuid, eier)
);


CREATE TABLE IF NOT EXISTS soknadmal
(
    prosessnavn    varchar(256)                                                      NOT NULL,
    prosessversjon integer                                                           NOT NULL,
    mal            jsonb                                                             NOT NULL,
    opprettet      timestamp WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::text) NOT NULL,
    PRIMARY KEY (prosessnavn, prosessversjon)
);



CREATE TABLE IF NOT EXISTS soknad_tekst_v1
(
    id    bigserial PRIMARY KEY,
    uuid  varchar(36) UNIQUE REFERENCES soknad_v1 (uuid),
    tekst jsonb NOT NULL
);


CREATE TABLE IF NOT EXISTS dokument_v1
(
    id                bigserial PRIMARY KEY,
    soknad_uuid       varchar(36)  NOT NULL REFERENCES soknad_v1 (uuid),
    dokument_lokasjon varchar(256) NOT NULL UNIQUE
);


CREATE INDEX IF NOT EXISTS dokument_v1_soknad_uuid_idx
    ON dokument_v1 (soknad_uuid);



CREATE TABLE IF NOT EXISTS dokumentkrav_v1
(
    faktum_id       varchar                                           NOT NULL,
    soknad_uuid     varchar(36)                                       NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    beskrivende_id  varchar                                           NOT NULL,
    faktum          jsonb                                             NOT NULL,
    sannsynliggjoer jsonb                                             NOT NULL,
    tilstand        varchar                                           NOT NULL,
    valg            varchar DEFAULT 'IKKE_BESVART'::character varying NOT NULL,
    begrunnelse     text,
    PRIMARY KEY (faktum_id, soknad_uuid)
);


CREATE TABLE IF NOT EXISTS dokumentkrav_filer_v1
(
    faktum_id   varchar                  NOT NULL,
    soknad_uuid varchar(36)              NOT NULL,
    filnavn     varchar                  NOT NULL,
    storrelse   bigint                   NOT NULL,
    urn         varchar                  NOT NULL,
    tidspunkt   timestamp WITH TIME ZONE NOT NULL,
    PRIMARY KEY (faktum_id, soknad_uuid, urn),
    FOREIGN KEY (faktum_id, soknad_uuid) REFERENCES dokumentkrav_v1
);



CREATE TABLE IF NOT EXISTS aktivitetslogg_v1
(
    id          bigserial PRIMARY KEY,
    soknad_uuid varchar(36)
        CONSTRAINT soknad_uuid UNIQUE REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    data        jsonb NOT NULL
);


