
ALTER TABLE innsending_v1
    ADD COLUMN sist_endret_tilstand TIMESTAMP;

UPDATE innsending_v1 SET sist_endret_tilstand = innsendt WHERE sist_endret_tilstand IS NULL;

ALTER TABLE innsending_v1 ALTER COLUMN sist_endret_tilstand SET NOT NULL;