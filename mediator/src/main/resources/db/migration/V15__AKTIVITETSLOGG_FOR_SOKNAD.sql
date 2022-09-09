/**
  aktivitetslogg_v1 lar loggen vokse evig, og etterhvert tar det mye tid å lagre den.
  aktivitetslogg_v2 lagrer loggen behandling for behandling, og akkumulerer loggen i etterkant.
  aktivitetslogg_v3 lagrer aktivitetslogg per soknad
 */
CREATE TABLE IF NOT EXISTS aktivitetslogg_v3
(
    id BIGSERIAL PRIMARY KEY,
    soknad_uuid VARCHAR(36) REFERENCES soknad_v1(uuid),
    data jsonb NOT NULL
);