CREATE TABLE IF NOT EXISTS soknad_tekst_v1
(
    id    BIGSERIAL PRIMARY KEY,
    uuid  VARCHAR(36) REFERENCES soknad_v1 (uuid) UNIQUE,
    tekst JSONB NOT NULL
);