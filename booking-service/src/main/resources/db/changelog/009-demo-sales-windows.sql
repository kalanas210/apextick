--liquibase formatted sql

--changeset apextick:009-01-demo-sales-windows context:demo runAlways:true
-- Holds, orders and payments now refuse an event once it has kicked off, once its sales
-- window has closed, or before it opens (SalesWindow). The demo catalog in 005 carries
-- fixed 2026 dates, so on any deployment made after them every fixture would be both
-- already played and unbuyable -- the guard would be correct and the demo dead.
--
-- So roll the whole seeded season forward instead of hard-coding new dates: move every
-- seeded fixture by the same number of whole weeks, enough to put the earliest one at
-- least two weeks out, which keeps the original spacing (group stage, then semis, then
-- finals) and the per-series ordering intact.
--
-- Whole weeks on each fixture's own local calendar, because the kickoff is part of the
-- fixture: India v Pakistan starts at 19:00 in Ahmedabad, and a Saturday three o'clock at
-- the Etihad has to stay a Saturday at three. Moving the season by the raw distance to a
-- midnight put the earliest fixture at 00:00 and every other kickoff at the same odd
-- offset; a fixed number of hours slides a London or New York kickoff by an hour whenever
-- the roll crosses a daylight-saving change; and a number of days that is not a number of
-- weeks turns Saturdays into Sundays. A zone Postgres does not know falls back to UTC (the
-- admin API accepts anything java.time does, offsets included), so an operator setting
-- an odd zone on a seeded fixture cannot stop a boot that runs this every time.
--
-- runAlways is the point of this changeset, not a detail. Run once, it fixes only the day
-- it ran: the clock keeps moving, and two or three weeks later the headline fixture
-- (india-pakistan-group-stage) kicks off and starts refusing every hold, order and payment
-- with SALES_CLOSED, with the rest following one at a time over the seeded season -- a
-- catalog you can browse and nothing you can buy. Re-running it on every boot re-rolls the
-- season, so a long-lived demo stays holdable, payable and scannable. GREATEST(..., 0)
-- keeps it from ever shifting the season backwards, and the shift is recomputed from
-- min(starts_at) each time, so the result is the same wherever it starts from: idempotent
-- within a day, self-healing after one. runAlways carries a second thing this needs: it
-- also exempts this changeset from checksum validation, and the deployment that needs
-- healing is by definition one migrated by an earlier revision of this file. Without that
-- exemption a changed checksum makes Liquibase refuse the whole update, and a service that
-- will not start heals nothing. runOnChange grants that same exemption and nothing more, so
-- this carries one flag rather than two.
--
-- (Keep every line here from starting with a Liquibase directive word: the formatted-SQL
-- parser reads "-- changeset ..." as a declaration even mid-comment and fails the parse.)
--
-- Sales then open a week ago and close at the gates, so the demo shows a real, open sales
-- window rather than two NULLs. Only the twenty events the 005 seed creates are touched;
-- anything an operator created through the admin panel keeps its own dates.
WITH seeded AS (
  SELECT id, starts_at,
         CASE WHEN time_zone IN (SELECT name FROM pg_timezone_names) THEN time_zone ELSE 'UTC' END AS zone
    FROM events
   WHERE slug IN (
     'india-pakistan-group-stage', 'australia-england-super-8', 'south-africa-new-zealand-super-8',
     'india-australia-semi-final', 'world-cup-final', 'mumbai-indians-chennai-super-kings',
     'bengaluru-kolkata-night', 'gujarat-titans-rajasthan-royals', 'chennai-mumbai-return',
     'qualifier-one', 'arsenal-manchester-city', 'liverpool-manchester-united',
     'manchester-city-tottenham', 'newcastle-chelsea', 'aston-villa-arsenal',
     'tottenham-chelsea-derby', 'usa-paraguay-group-stage', 'brazil-morocco-group-stage',
     'england-croatia-group-stage', 'world-cup-final-2026')
), shift AS (
  -- the days that put the earliest fixture a fortnight out, rounded up to whole weeks
  SELECT GREATEST(((now() + INTERVAL '14 days')::date - min(starts_at)::date + 6) / 7, 0) * 7 AS days
    FROM seeded
), rolled AS (
  SELECT seeded.id,
         ((seeded.starts_at AT TIME ZONE seeded.zone) + shift.days * INTERVAL '1 day')
           AT TIME ZONE seeded.zone AS starts_at
    FROM seeded CROSS JOIN shift
)
UPDATE events e SET
  starts_at      = rolled.starts_at,
  sales_start_at = date_trunc('day', now()) - INTERVAL '7 days',
  sales_end_at   = rolled.starts_at - INTERVAL '2 hours',
  updated_at     = now()
  FROM rolled
 WHERE e.id = rolled.id;
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
