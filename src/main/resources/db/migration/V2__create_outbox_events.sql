CREATE TABLE outbox_events (
    id           VARCHAR(36)  NOT NULL,
    aggregate_id VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    topic        VARCHAR(255) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   TEXT,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_outbox_events_attempts_non_negative CHECK (attempts >= 0)
);

-- Only pending events are indexed, so the relay's lookup stays small no matter how many were already published.
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
