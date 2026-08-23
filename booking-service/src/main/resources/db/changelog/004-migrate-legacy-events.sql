--liquibase formatted sql

--changeset apextick:004-01-backfill-events
UPDATE events SET
  slug = lower(regexp_replace(name, '[^a-zA-Z0-9]+', '-', 'g')) || '-' || id,
  sport = COALESCE(sport, 'CRICKET'),
  status = 'DRAFT',
  currency = COALESCE(currency, 'USD'),
  time_zone = COALESCE(time_zone, 'UTC'),
  city = COALESCE(city, ''),
  country = COALESCE(country, ''),
  stage = COALESCE(stage, 'Demo'),
  blurb = COALESCE(blurb, ''),
  image = COALESCE(image, '1540747913346-19e32dc3e97e'),
  created_at = COALESCE(created_at, now())
WHERE slug IS NULL;
--rollback UPDATE events SET slug = NULL WHERE stage = 'Demo';

--changeset apextick:004-02-default-layout
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, 'standard', 'Standard', 10.00, '[]', 0
FROM events e
WHERE NOT EXISTS (SELECT 1 FROM price_tiers pt WHERE pt.event_id = e.id);
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, 'main', 'Main Stand',
       (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id ORDER BY pt.id LIMIT 1),
       'n',
       COALESCE((SELECT MAX(ascii(substring(s.seat_number from '^[A-Za-z]')) - 64) FROM seats s WHERE s.event_id = e.id), 1),
       COALESCE((SELECT MAX(CAST(substring(s.seat_number from '[0-9]+$') AS INT)) FROM seats s WHERE s.event_id = e.id), 1),
       0
FROM events e
WHERE NOT EXISTS (SELECT 1 FROM sections sec WHERE sec.event_id = e.id);
UPDATE seats s SET
  section_id = (SELECT sec.id FROM sections sec WHERE sec.event_id = s.event_id ORDER BY sec.id LIMIT 1),
  row_idx = COALESCE(ascii(substring(s.seat_number from '^[A-Za-z]')) - 65, 0),
  col_idx = COALESCE(CAST(substring(s.seat_number from '[0-9]+$') AS INT) - 1, 0)
WHERE s.section_id IS NULL;
--rollback UPDATE seats SET section_id = NULL, row_idx = NULL, col_idx = NULL;

--changeset apextick:004-03-constraints
ALTER TABLE seats DROP CONSTRAINT uq_event_seat;
ALTER TABLE seats ADD CONSTRAINT uq_section_seat UNIQUE (section_id, seat_number);
ALTER TABLE seats ALTER COLUMN section_id SET NOT NULL;
ALTER TABLE seats ALTER COLUMN row_idx SET NOT NULL;
ALTER TABLE seats ALTER COLUMN col_idx SET NOT NULL;
ALTER TABLE events ALTER COLUMN slug SET NOT NULL;
ALTER TABLE events ALTER COLUMN sport SET NOT NULL;
ALTER TABLE events ADD CONSTRAINT uq_events_slug UNIQUE (slug);
--rollback ALTER TABLE seats DROP CONSTRAINT uq_section_seat;
