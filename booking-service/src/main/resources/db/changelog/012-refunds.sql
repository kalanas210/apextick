--liquibase formatted sql

--changeset apextick:012-01-order-refunds
-- PAID used to be the end of the line: nothing could give a paid order its money back. An order
-- refunded by the box office now says so, with when, by whom and why.
ALTER TABLE orders ADD COLUMN refunded_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN refunded_by VARCHAR(64);
ALTER TABLE orders ADD COLUMN refund_reason VARCHAR(255);
--rollback ALTER TABLE orders DROP COLUMN refund_reason, DROP COLUMN refunded_by, DROP COLUMN refunded_at;

--changeset apextick:012-02-payment-refund-retries
-- REFUND_REQUIRED was written by two code paths and read by none. A refund still owed is now asked
-- for again: what is owed is kept (a mis-priced charge owes what it took, not what the order cost),
-- with how many times the provider has been asked and what it last said, and the partial index
-- lets the retry job find the refunds that are due without reading every payment.
ALTER TABLE payments ADD COLUMN refund_amount NUMERIC(12,2);
ALTER TABLE payments ADD COLUMN refund_currency VARCHAR(3);
ALTER TABLE payments ADD COLUMN refund_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN refund_last_attempt_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN refund_error VARCHAR(512);
CREATE INDEX idx_payments_refund_required ON payments (refund_last_attempt_at)
  WHERE status = 'REFUND_REQUIRED';
--rollback DROP INDEX idx_payments_refund_required;
--rollback ALTER TABLE payments DROP COLUMN refund_error, DROP COLUMN refund_last_attempt_at, DROP COLUMN refund_attempts, DROP COLUMN refund_currency, DROP COLUMN refund_amount;
