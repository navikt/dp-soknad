CREATE TABLE IF NOT EXISTS person_v1
(
    id    bigserial PRIMARY KEY,
    ident varchar(11) NOT NULL UNIQUE
);


CREATE TABLE IF NOT EXISTS soknad_v1
(
    id                    bigserial PRIMARY KEY,
    uuid                  uuid                                                              NOT NULL UNIQUE,
    person_ident          varchar(11)                                                       NOT NULL REFERENCES person_v1 (ident),
    tilstand              varchar(50)                                                       NOT NULL,
    opprettet             timestamp WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::text) NOT NULL,
    spraak                text                     DEFAULT 'NB'::text                       NOT NULL,
    sist_endret_av_bruker timestamp WITH TIME ZONE
);



CREATE TABLE IF NOT EXISTS soknad_cache
(
    uuid        uuid                                                              NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
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
    uuid  uuid UNIQUE REFERENCES soknad_v1 (uuid),
    tekst jsonb NOT NULL
);


CREATE TABLE IF NOT EXISTS dokumentkrav_v1
(
    faktum_id       varchar                                           NOT NULL,
    soknad_uuid     uuid                                              NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
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
    soknad_uuid uuid                     NOT NULL,
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
    soknad_uuid uuid
        CONSTRAINT soknad_uuid UNIQUE REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    data        jsonb NOT NULL
);

CREATE TYPE brevkode AS
(
    tittel varchar,
    skjemakode varchar
);

CREATE TABLE IF NOT EXISTS innsending_v1
(
    id              bigserial PRIMARY KEY,
    innsending_uuid uuid UNIQUE,
    innsendt        timestamp WITH TIME ZONE NOT NULL,
    journalpost_id  varchar UNIQUE           NULL,
    innsendingtype  varchar                  NOT NULL,
    tilstand        varchar                  NOT NULL,
    brevkode        brevkode                NULL
);


