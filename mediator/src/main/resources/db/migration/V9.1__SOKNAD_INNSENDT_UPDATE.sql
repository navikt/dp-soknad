UPDATE soknad_v1
SET innsendt = i.innsendt
FROM innsending_v1 AS i
WHERE uuid = i.soknad_uuid
  AND i.innsendingtype = 'NY_DIALOG';