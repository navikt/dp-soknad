ALTER TABLE dokumentkrav_filer_v1
    DROP CONSTRAINT dokumentkrav_filer_v1_faktum_id_soknad_uuid_fkey;


ALTER TABLE dokumentkrav_filer_v1
    ADD FOREIGN KEY (faktum_id, soknad_uuid) REFERENCES dokumentkrav_v1 ON DELETE CASCADE;

