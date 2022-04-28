CREATE TABLE IF NOT EXISTS soknad_v1 (
    id BIGSERIAL PRIMARY KEY ,
    uuid VARCHAR(36) UNIQUE NOT NULL,
    person_ident VARCHAR(11) REFERENCES person_v1(ident) NOT NULL,
    tilstand VARCHAR(50) NOT NULL,
    dokument_lokasjon VARCHAR(256) NULL,
    journalpost_id BIGINT UNIQUE NULL
);
