CREATE TABLE IF NOT EXISTS dokumenter_v1
(
    id                BIGSERIAL PRIMARY KEY,
    soknad_uuid       VARCHAR(36)         NOT NULL REFERENCES soknad_v1 (uuid),
    variant           VARCHAR(256)        NULL,
    dokument_lokasjon VARCHAR(256) UNIQUE NOT NULL
)