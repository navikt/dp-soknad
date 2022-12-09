ALTER TABLE soknad_v1
    ADD COLUMN innsendt TIMESTAMP WITH TIME ZONE NULL;

-- TODO
-- UPDATE soknad_v1 AS s
-- SET s.innsendt = i.innsendt
--     FROM innsending_v1 AS i
-- WHERE s.uuid = i.soknad_uuid
--   AND i.innsendt = 'NY_DIALOG';