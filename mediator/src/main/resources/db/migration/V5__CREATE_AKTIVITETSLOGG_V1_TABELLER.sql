CREATE TABLE IF NOT EXISTS aktivitetslogg_v1
(
    id   BIGINT PRIMARY KEY REFERENCES person_v1,
    data JSONB NOT NULL
);
