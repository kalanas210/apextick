--liquibase formatted sql

--changeset apextick:010-01-payment-idempotency-key
-- One attempt per key per order. A repeated key now gets the answer its first attempt got
-- instead of a second charge, and this index keeps that true should two requests ever get
-- past the order lock together. The key used to be optional and unconstrained, so an older
-- payment may already share one with another on the same order; those later duplicates get a
-- suffix no client will ever send, rather than failing the index and with it the boot.
UPDATE payments p
   SET idempotency_key = left(p.idempotency_key, 100) || ':dup:' || d.rn
  FROM (SELECT id, row_number() OVER (PARTITION BY order_id, idempotency_key ORDER BY created_at, id) AS rn
          FROM payments
         WHERE idempotency_key IS NOT NULL) d
 WHERE p.id = d.id AND d.rn > 1;
CREATE UNIQUE INDEX uq_payments_order_idempotency_key ON payments (order_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
--rollback DROP INDEX uq_payments_order_idempotency_key;
