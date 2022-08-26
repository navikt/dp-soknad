ALTER TABLE dokumentkrav_v1 DROP CONSTRAINT dokumentkrav_v1_pkey;
ALTER TABLE dokumentkrav_v1 ADD PRIMARY KEY (faktum_id, soknad_uuid);