CREATE TABLE IF NOT EXISTS person_v1
(
    id    BIGSERIAL PRIMARY KEY,
    ident VARCHAR(11) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS soknad_v1
(
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  uuid                                                              NOT NULL UNIQUE,
    person_ident          VARCHAR(11)                                                       NOT NULL REFERENCES person_v1 (ident),
    tilstand              VARCHAR(50)                                                       NOT NULL,
    opprettet             TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    spraak                TEXT                     DEFAULT 'NB'::TEXT                       NOT NULL,
    sist_endret_av_bruker TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS soknad_data
(
    uuid        uuid                                                              NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    eier        VARCHAR(20)                                                       NOT NULL,
    soknad_data jsonb                                                             NOT NULL,
    mottatt     TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    sist_endret TIMESTAMP                DEFAULT NOW()                            NOT NULL,
    versjon     INT                                                               NOT NULL DEFAULT 1,
    PRIMARY KEY (uuid, eier)
);

CREATE TABLE IF NOT EXISTS soknadmal
(
    prosessnavn    VARCHAR(256)                                                      NOT NULL,
    prosessversjon INTEGER                                                           NOT NULL,
    mal            jsonb                                                             NOT NULL,
    opprettet      TIMESTAMP WITH TIME ZONE DEFAULT (NOW() AT TIME ZONE 'utc'::TEXT) NOT NULL,
    PRIMARY KEY (prosessnavn, prosessversjon)
);

CREATE TABLE IF NOT EXISTS soknad_tekst_v1
(
    id    BIGSERIAL PRIMARY KEY,
    uuid  uuid UNIQUE REFERENCES soknad_v1 (uuid),
    tekst jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS dokumentkrav_v1
(
    faktum_id       VARCHAR NOT NULL,
    soknad_uuid     uuid    NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    beskrivende_id  VARCHAR NOT NULL,
    faktum          jsonb   NOT NULL,
    sannsynliggjoer jsonb   NOT NULL,
    tilstand        VARCHAR NOT NULL,
    valg            VARCHAR      DEFAULT 'IKKE_BESVART'::CHARACTER VARYING NOT NULL,
    begrunnelse     TEXT,
    bundle_urn      TEXT    NULL DEFAULT NULL,
    PRIMARY KEY (faktum_id, soknad_uuid)
);


CREATE TABLE IF NOT EXISTS dokumentkrav_filer_v1
(
    faktum_id   VARCHAR                  NOT NULL,
    soknad_uuid uuid                     NOT NULL,
    filnavn     VARCHAR                  NOT NULL,
    storrelse   BIGINT                   NOT NULL,
    urn         VARCHAR                  NOT NULL,
    tidspunkt   TIMESTAMP WITH TIME ZONE NOT NULL,
    bundlet     bool                     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (faktum_id, soknad_uuid, urn),
    FOREIGN KEY (faktum_id, soknad_uuid) REFERENCES dokumentkrav_v1
);

CREATE TABLE IF NOT EXISTS aktivitetslogg_v1
(
    id          BIGSERIAL PRIMARY KEY,
    soknad_uuid uuid
        CONSTRAINT soknad_uuid UNIQUE REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    data        jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS innsending_v1
(
    id              BIGSERIAL PRIMARY KEY,
    innsending_uuid uuid UNIQUE,
    soknad_uuid     uuid REFERENCES soknad_v1 (uuid),
    innsendt        TIMESTAMP WITH TIME ZONE NOT NULL,
    journalpost_id  VARCHAR UNIQUE           NULL,
    innsendingtype  VARCHAR                  NOT NULL,
    tilstand        VARCHAR                  NOT NULL
);

CREATE TABLE IF NOT EXISTS metadata
(
    innsending_uuid uuid REFERENCES innsending_v1 (innsending_uuid) ON DELETE CASCADE NOT NULL PRIMARY KEY,
    skjemakode      TEXT DEFAULT NULL,
    tittel          TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS dokument_v1
(
    dokument_uuid   uuid PRIMARY KEY,
    innsending_uuid uuid REFERENCES innsending_v1 (innsending_uuid),
    brevkode        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS hoveddokument_v1
(
    id              BIGSERIAL PRIMARY KEY,
    innsending_uuid uuid UNIQUE REFERENCES innsending_v1 (innsending_uuid),
    dokument_uuid   uuid REFERENCES dokument_v1 (dokument_uuid)
);

CREATE TABLE IF NOT EXISTS dokumentvariant_v1
(
    dokumentvariant_uuid uuid PRIMARY KEY,
    dokument_uuid        uuid REFERENCES dokument_v1 (dokument_uuid),
    filnavn              TEXT NOT NULL,
    urn                  TEXT NOT NULL,
    variant              TEXT NOT NULL,
    type                 TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ettersending_v1
(
    innsending_uuid   uuid REFERENCES innsending_v1 (innsending_uuid),
    ettersending_uuid uuid REFERENCES innsending_v1 (innsending_uuid),
    PRIMARY KEY (innsending_uuid, ettersending_uuid)
);
