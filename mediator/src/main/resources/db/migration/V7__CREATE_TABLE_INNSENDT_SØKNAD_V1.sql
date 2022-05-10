CREATE TABLE IF NOT EXISTS innsendt_soknad_v1
(
    id               BIGSERIAL PRIMARY KEY,
    uuid             VARCHAR(36) REFERENCES soknad_v1 (uuid),
    soknad_med_tekst JSONB NOT NULL
);
