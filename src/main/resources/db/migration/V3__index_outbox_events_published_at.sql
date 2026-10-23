-- Only published events are indexed, so the cleanup finds expired rows without scanning the table.
CREATE INDEX idx_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
