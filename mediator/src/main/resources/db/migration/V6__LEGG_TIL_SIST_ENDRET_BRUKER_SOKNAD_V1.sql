ALTER TABLE soknad_v1
    ADD COLUMN sist_endret_av_bruker TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc');