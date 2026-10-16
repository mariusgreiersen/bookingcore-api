-- Reservable resources such as meeting rooms and workspaces.
CREATE TABLE resources
(
    id          UUID          NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(1000),
    location    VARCHAR(200),
    capacity    INTEGER       NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,

    CONSTRAINT pk_resources PRIMARY KEY (id),
    CONSTRAINT chk_resources_capacity_positive CHECK (capacity > 0),
    CONSTRAINT chk_resources_name_not_blank CHECK (length(btrim(name)) > 0)
);
