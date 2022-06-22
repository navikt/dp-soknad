/**
  aktivitetslogg_v1 lar loggen vokse evig, og etterhvert tar det mye tid å lagre den.
  aktivitetslogg_v2 lagrer loggen behandling for behandling, og akkumulerer loggen i etterkant.
 */
CREATE TABLE IF NOT EXISTS aktivitetslogg_v2
(
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT REFERENCES person_v1,
    data jsonb NOT NULL
);

INSERT INTO aktivitetslogg_v2 (person_id, data)
SELECT id, data
FROM aktivitetslogg_v1;