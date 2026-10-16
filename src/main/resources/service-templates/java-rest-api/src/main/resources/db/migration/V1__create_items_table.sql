-- The example resource. Replace it with your service's own tables.
CREATE TABLE items (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL
);
