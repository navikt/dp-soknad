ALTER TABLE dokumentkrav_v1 ADD COLUMN innsendt BOOLEAN NOT NULL default false;
UPDATE dokumentkrav_v1 SET innsendt = true;
