-- ${flyway:timestamp} - trigger ny oppdatering
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_roles WHERE rolname = 'cloudsqliamuser')
        THEN
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
            GRANT ALL ON TABLE soknadmal TO cloudsqliamuser;
        END IF;
    END
$$;