--liquibase formatted sql

--changeset apextick:001-01-processed-events
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  type VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
--rollback DROP TABLE processed_events;

--changeset apextick:001-02-notifications
CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  event_id UUID,
  type VARCHAR(64),
  recipient VARCHAR(255),
  subject VARCHAR(255),
  status VARCHAR(16) NOT NULL,
  error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_event ON notifications (event_id);
--rollback DROP TABLE notifications;
