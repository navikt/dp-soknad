ALTER TABLE soknadmal
    DROP CONSTRAINT soknadmal_pkey,
    ADD COLUMN id BIGSERIAL PRIMARY KEY,
    ADD UNIQUE (prosessnavn, prosessversjon);

ALTER TABLE soknad_v1
    ADD COLUMN soknadmal BIGINT REFERENCES soknadmal (id);