ALTER TABLE soknad_cache
    DROP COLUMN IF EXISTS mottatt;

ALTER TABLE soknad_cache
    ADD COLUMN IF NOT EXISTS
        mottatt TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT (NOW());