CREATE TABLE IF NOT EXISTS dokument_v1
(
    id                BIGSERIAL PRIMARY KEY,
    soknad_uuid       VARCHAR(36)         NOT NULL REFERENCES soknad_v1 (uuid),
    dokument_lokasjon VARCHAR(256) UNIQUE NOT NULL
)