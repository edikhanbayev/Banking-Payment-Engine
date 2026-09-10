CREATE TABLE outbox_events
(
    id              UUID PRIMARY KEY,

    aggregate_type  VARCHAR(50) NOT NULL,

    aggregate_id    UUID NOT NULL,

    event_type      VARCHAR(100) NOT NULL,

    topic           VARCHAR(100) NOT NULL,

    event_key       VARCHAR(100) NOT NULL,

    payload         JSONB NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    published_at    TIMESTAMPTZ,

    attempt_count   INTEGER NOT NULL DEFAULT 0,

    last_error      VARCHAR(1000),

    CONSTRAINT chk_outbox_attempt_count
        CHECK (attempt_count >= 0)
);


CREATE INDEX idx_outbox_unpublished
    ON outbox_events(created_at)
    WHERE published_at IS NULL;
