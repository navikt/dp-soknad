CREATE TABLE IF NOT EXISTS soknadmal (
    prosessnavn VARCHAR(256) NOT NULL,
    prosessversjon INT NOT NULL,
    mal JSONB NOT NULL,
    opprettet TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    PRIMARY KEY (prosessnavn, prosessversjon)
)
