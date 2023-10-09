CREATE INDEX idx_soknad_v1_soknad_oppslag
    ON soknad_v1 (person_ident, tilstand, sist_endret_av_bruker DESC);
