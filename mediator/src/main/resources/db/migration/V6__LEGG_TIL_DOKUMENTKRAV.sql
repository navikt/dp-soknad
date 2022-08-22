CREATE TABLE IF NOT EXISTS sannsynliggjoering_v1
(
    faktum_id       VARCHAR     NOT NULL PRIMARY KEY,
    soknad_uuid     VARCHAR(36) NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    faktum          JSONB       NOT NULL,
    sannsynliggjoer JSONB       NOT NULL
);

CREATE TABLE IF NOT EXISTS dokumentkrav_v1
(

    faktum_id      VARCHAR     NOT NULL PRIMARY KEY,
    beskrivende_id VARCHAR     NOT NULL,
    soknad_uuid    VARCHAR(36) NOT NULL REFERENCES soknad_v1 (uuid) ON DELETE CASCADE,
    fakta          JSONB       NOT NULL
);


