CREATE TABLE IF NOT EXISTS dokumentkrav_v1
(
    faktum_id       VARCHAR     NOT NULL PRIMARY KEY,
    soknad_uuid     VARCHAR(36) NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    beskrivende_id  VARCHAR     NOT NULL,
    faktum          JSONB       NOT NULL,
    sannsynliggjoer JSONB       NOT NULL,
    tilstand        VARCHAR     NOT NULL
);


