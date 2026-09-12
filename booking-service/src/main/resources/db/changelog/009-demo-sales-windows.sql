--liquibase formatted sql

--changeset apextick:009-01-demo-sales-windows context:demo
-- Holds, orders and payments now refuse an event once it has kicked off, once its sales
-- window has closed, or before it opens (SalesWindow). The demo catalog in 005 carries
-- fixed 2026 dates, so on any deployment made after them every fixture would be both
-- already played and unbuyable -- the guard would be correct and the demo dead.
--
-- So roll the whole seeded season forward at migration time instead of hard-coding new
-- dates: shift every seeded fixture by the same interval, enough to put the earliest one
-- two weeks out, which keeps the original spacing (group stage, then semis, then finals)
-- and the per-series ordering intact. GREATEST(..., 0) makes it a no-op on a database
-- where the seed is already in the future, so a fresh deployment and an upgrade of a
-- running one land in the same place.
--
-- Sales then open a week ago and close at the gates, so the demo shows a real, open sales
-- window rather than two NULLs. Only the twenty events the 005 seed creates are touched;
-- anything an operator created through the admin panel keeps its own dates.
UPDATE events e SET
  starts_at      = e.starts_at + shift.delta,
  sales_start_at = date_trunc('day', now()) - INTERVAL '7 days',
  sales_end_at   = e.starts_at + shift.delta - INTERVAL '2 hours',
  updated_at     = now()
FROM (
  SELECT GREATEST(
           (date_trunc('day', now()) + INTERVAL '14 days') - min(starts_at),
           INTERVAL '0'
         ) AS delta
    FROM events
   WHERE slug IN (
     'india-pakistan-group-stage', 'australia-england-super-8', 'south-africa-new-zealand-super-8',
     'india-australia-semi-final', 'world-cup-final', 'mumbai-indians-chennai-super-kings',
     'bengaluru-kolkata-night', 'gujarat-titans-rajasthan-royals', 'chennai-mumbai-return',
     'qualifier-one', 'arsenal-manchester-city', 'liverpool-manchester-united',
     'manchester-city-tottenham', 'newcastle-chelsea', 'aston-villa-arsenal',
     'tottenham-chelsea-derby', 'usa-paraguay-group-stage', 'brazil-morocco-group-stage',
     'england-croatia-group-stage', 'world-cup-final-2026')
) AS shift
WHERE e.slug IN (
  'india-pakistan-group-stage', 'australia-england-super-8', 'south-africa-new-zealand-super-8',
  'india-australia-semi-final', 'world-cup-final', 'mumbai-indians-chennai-super-kings',
  'bengaluru-kolkata-night', 'gujarat-titans-rajasthan-royals', 'chennai-mumbai-return',
  'qualifier-one', 'arsenal-manchester-city', 'liverpool-manchester-united',
  'manchester-city-tottenham', 'newcastle-chelsea', 'aston-villa-arsenal',
  'tottenham-chelsea-derby', 'usa-paraguay-group-stage', 'brazil-morocco-group-stage',
  'england-croatia-group-stage', 'world-cup-final-2026');
-- Rolling back restores the two NULL sales windows on the seeded fixtures only; starts_at
-- keeps the rolled-forward date, because the shift is computed from now() and the original
-- is not recorded anywhere. Scoped to the same twenty slugs as the changeset: every other
-- event's sales window belongs to whoever set it.
--rollback UPDATE events SET sales_start_at = NULL, sales_end_at = NULL WHERE slug IN (
--rollback   'india-pakistan-group-stage', 'australia-england-super-8', 'south-africa-new-zealand-super-8',
--rollback   'india-australia-semi-final', 'world-cup-final', 'mumbai-indians-chennai-super-kings',
--rollback   'bengaluru-kolkata-night', 'gujarat-titans-rajasthan-royals', 'chennai-mumbai-return',
--rollback   'qualifier-one', 'arsenal-manchester-city', 'liverpool-manchester-united',
--rollback   'manchester-city-tottenham', 'newcastle-chelsea', 'aston-villa-arsenal',
--rollback   'tottenham-chelsea-derby', 'usa-paraguay-group-stage', 'brazil-morocco-group-stage',
--rollback   'england-croatia-group-stage', 'world-cup-final-2026');
