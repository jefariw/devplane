-- The GitHub repository provisioned for a service, if any. The three columns are
-- set together when the repository is created, and never one without the others.
ALTER TABLE services
    ADD COLUMN repository_url        varchar(255),
    ADD COLUMN repository_template   varchar(63),
    ADD COLUMN repository_created_at timestamptz,
    ADD CONSTRAINT services_repository_check CHECK (
        (repository_url IS NULL AND repository_template IS NULL AND repository_created_at IS NULL)
        OR (repository_url IS NOT NULL AND repository_template IS NOT NULL AND repository_created_at IS NOT NULL)
    );
