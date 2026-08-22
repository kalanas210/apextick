--liquibase formatted sql

--changeset apextick:006-01-outbox
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  type VARCHAR(64) NOT NULL,
  payload JSONB NOT NULL,
  headers JSONB,
  correlation_id VARCHAR(64),
  occurred_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_error TEXT,
  dead_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events (next_attempt_at) WHERE published_at IS NULL AND dead_at IS NULL;
--rollback DROP TABLE outbox_events;
