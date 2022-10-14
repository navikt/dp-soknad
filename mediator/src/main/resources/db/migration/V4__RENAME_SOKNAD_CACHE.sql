ALTER TABLE dokumentkrav_v1
    ADD COLUMN IF NOT EXISTS bundle_urn VARCHAR NULL DEFAULT NULL;

ALTER TABLE soknad_cache
    RENAME TO soknad_data