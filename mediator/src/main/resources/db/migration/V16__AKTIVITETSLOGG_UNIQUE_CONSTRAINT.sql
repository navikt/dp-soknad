TRUNCATE aktivitetslogg_v3;

ALTER TABLE aktivitetslogg_v3
    ADD CONSTRAINT soknad_uuid UNIQUE (soknad_uuid)
