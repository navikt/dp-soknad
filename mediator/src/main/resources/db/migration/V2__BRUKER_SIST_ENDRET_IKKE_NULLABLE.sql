UPDATE soknad_v1 SET sist_endret_av_bruker = opprettet WHERE sist_endret_av_bruker IS NULL;

ALTER TABLE soknad_v1 ALTER COLUMN sist_endret_av_bruker SET NOT NULL;