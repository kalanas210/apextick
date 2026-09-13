--liquibase formatted sql

--changeset apextick:013-01-event-cancellation
-- Cancelling an event used to flip a badge and nothing else. It now refunds and cancels
-- everything sold for the event, so the event keeps when it was called off and the reason its
-- ticket holders were given.
ALTER TABLE events ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN cancel_reason VARCHAR(255);
--rollback ALTER TABLE events DROP COLUMN cancel_reason, DROP COLUMN cancelled_at;
