-- The service catalog. The API identifies services by name; the surrogate id
-- stays internal so later tables can reference services cheaply.
CREATE TABLE services (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar(63)  NOT NULL,
    owner       varchar(63)  NOT NULL,
    description varchar(300),
    lifecycle   varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT services_name_key UNIQUE (name),
    CONSTRAINT services_lifecycle_check
        CHECK (lifecycle IN ('EXPERIMENTAL', 'PRODUCTION', 'DEPRECATED'))
);

-- Supports listing services by owner.
CREATE INDEX services_owner_idx ON services (owner);
