-- When the relay may try the event again; pushed forward after each failed send (exponential backoff).
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ;
UPDATE outbox_events SET next_attempt_at = created_at;
ALTER TABLE outbox_events ALTER COLUMN next_attempt_at SET NOT NULL;

-- Set when the relay gives up after the maximum number of attempts. The event is kept for investigation.
ALTER TABLE outbox_events ADD COLUMN failed_at TIMESTAMPTZ;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_published_or_failed CHECK (published_at IS NULL OR failed_at IS NULL);

-- Only events still due for delivery are indexed, so published and abandoned ones never slow the relay down.
DROP INDEX idx_outbox_events_unpublished;
CREATE INDEX idx_outbox_events_deliverable ON outbox_events (next_attempt_at)
    WHERE published_at IS NULL AND failed_at IS NULL;
