ALTER TABLE soknad_cache DROP CONSTRAINT soknad_cache_uuid_fkey;
ALTER TABLE soknad_cache ADD CONSTRAINT soknad_cache_uuid_fkey FOREIGN KEY (uuid) REFERENCES soknad_v1(uuid) ON DELETE CASCADE;