import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// ApexTick flash-sale load + correctness test.
//
// A crowd of virtual users races to hold seats on a single event through the SAME
// endpoint the UI uses: POST /api/events/{slug}/holds (multi-seat, all-or-nothing,
// rate-limited per user). Three outcomes are all correct:
//   201 you won the seat   409 someone beat you to it   429 the limiter throttled you
//
// What this proves (and how):
//   * holds_won == EXPECTED_SEATS   every seat was won exactly once. The endpoint is
//     all-or-nothing, so a 201 means the requested seats flipped AVAILABLE -> HELD in
//     one UPDATE; a second winner for any seat would push the counter ABOVE the
//     inventory and fail the threshold.
//   * teardown reads the seat map back through the admin API and checks each targeted
//     seat: it ends HELD, it has a holder, that holder is one of the load-test
//     identities, and its optimistic-lock `version` moved by exactly 1 since setup.
//     A seat two requests both "won" would have been written twice and show +2 -- that
//     is the double-booking detector. Final status alone cannot see it.
//   * the winners are spread over a pool of real Keycloak accounts, so "no seat was
//     held by two people" is a statement about people, not about one shared login.
//
// Auth is real: every identity logs into Keycloak (password grant on the confidential
// apextick-loadtest client -- the SPA's public client doesn't accept it) and calls the
// JWT-secured endpoints with a Bearer token, exactly like the app does.
//
// ---------------------------------------------------------------------------
// Running it
// ---------------------------------------------------------------------------
// Credentials are required, never defaulted:
//   LOADTEST_USER, LOADTEST_PASSWORD     the account to log in as (also the pool's password)
//   LOADTEST_CLIENT_SECRET               apextick-loadtest's secret (from .env)
//   LOADTEST_ADMIN_USER,                 an account holding the `admin` realm role; the
//   LOADTEST_ADMIN_PASSWORD              teardown reads /api/admin/seats with it
//                                        (grant it once: scripts/grant-admin.sh <user>)
//
// 1. Seed the identity pool once (creates loadtest-01..loadtest-NN in the realm):
//      ./seed-loadtest-users.sh 20
// 2. The hold endpoint is rate-limited (30/min per user by default), which is a
//    deliberate abuse control and is covered by its own test
//    (booking-service .../platform/RateLimitHttpTest.java). A full-inventory sweep
//    needs far more than 30 attempts per identity, so raise the limit for the run:
//      APP_RATE_LIMIT_HOLD_LIMIT=100000 docker compose up -d booking-service
//    Leaving it at 30 is also a valid run -- you then measure the limiter, and
//    holds_throttled reports how much of the storm it absorbed.
// 3. Run:
//      export LOADTEST_USER=kalana LOADTEST_PASSWORD=...
//      export LOADTEST_ADMIN_USER=kalana LOADTEST_ADMIN_PASSWORD=...
//      export LOADTEST_CLIENT_SECRET=$(sed -n 's/^LOADTEST_CLIENT_SECRET=//p' ../.env)
//      k6 run -e LOADTEST_USER_COUNT=20 booking-load-test.js
//
// Tunables (-e NAME=value):
//   BASE_URL KEYCLOAK_URL REALM CLIENT_ID EVENT_SLUG VUS ITERATIONS
//   LOADTEST_USER_COUNT   identities in the pool (default 1 = LOADTEST_USER only)
//   LOADTEST_USER_PREFIX  pool username prefix (default loadtest-, matching the seeder)
//   EXPECTED_SEATS        the event's inventory (default 300, the seeded demo event);
//                         setup fails loudly if the event doesn't have exactly this
//                         many AVAILABLE seats, so the threshold can never be vacuous
//   SEATS_PER_HOLD        seats per request (default 1). >1 exercises the multi-seat
//                         all-or-nothing path, but then a perfect sell-out is not
//                         expected -- isolated single seats can't satisfy a 2-seat
//                         request -- so the exact-inventory checks relax to "<=".
//
// The seats must start AVAILABLE: re-running needs the demo data reset (restart with a
// fresh volume, or release the holds). Nothing else may touch the event while it runs;
// the version-delta check assumes this test is the only writer.

const REQUIRED = [
  'LOADTEST_USER',
  'LOADTEST_PASSWORD',
  'LOADTEST_CLIENT_SECRET',
  'LOADTEST_ADMIN_USER',
  'LOADTEST_ADMIN_PASSWORD',
];
const missing = REQUIRED.filter((name) => !__ENV[name]);
if (missing.length > 0) {
  throw new Error(`Set ${missing.join(', ')} (k6 run -e NAME=value, or export them) -- see the header of this file.`);
}

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8180';
const REALM = __ENV.REALM || 'apextick';
const CLIENT_ID = __ENV.CLIENT_ID || 'apextick-loadtest';
const CLIENT_SECRET = __ENV.LOADTEST_CLIENT_SECRET;
const USERNAME = __ENV.LOADTEST_USER;
const PASSWORD = __ENV.LOADTEST_PASSWORD;
const ADMIN_USERNAME = __ENV.LOADTEST_ADMIN_USER;
const ADMIN_PASSWORD = __ENV.LOADTEST_ADMIN_PASSWORD;
const EVENT_SLUG = __ENV.EVENT_SLUG || 'india-australia-semi-final';
const VUS = Number(__ENV.VUS || 200);
const ITERATIONS = Number(__ENV.ITERATIONS || 5000);
const EXPECTED_SEATS = Number(__ENV.EXPECTED_SEATS || 300);
const SEATS_PER_HOLD = Number(__ENV.SEATS_PER_HOLD || 1);
const USER_COUNT = Number(__ENV.LOADTEST_USER_COUNT || 1);
const USER_PREFIX = __ENV.LOADTEST_USER_PREFIX || 'loadtest-';

// app.hold.max-seats caps a single hold; anything above it is a guaranteed 422, and a
// value above the inventory would spin forever picking distinct seats.
if (!(SEATS_PER_HOLD >= 1 && SEATS_PER_HOLD <= 8)) {
  throw new Error(`SEATS_PER_HOLD must be between 1 and 8 (app.hold.max-seats), got ${SEATS_PER_HOLD}`);
}

// With one seat per request the sale is a clean partition of the inventory: every seat
// can be won exactly once, so "won == inventory" and "nothing left AVAILABLE" are exact.
const STRICT_SELLOUT = SEATS_PER_HOLD === 1;

// The pool: LOADTEST_USER alone, or loadtest-01..loadtest-NN seeded by
// seed-loadtest-users.sh (which gives them all LOADTEST_PASSWORD).
const POOL = USER_COUNT > 1
  ? Array.from({ length: USER_COUNT }, (_, i) => `${USER_PREFIX}${String(i + 1).padStart(2, '0')}`)
  : [USERNAME];

// 201 (won), 409 (already taken) and 429 (throttled) are all correct server answers, as
// are the 200s from Keycloak and the admin seat map — don't count any of them as failed.
http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

const holdsWon = new Counter('holds_won');             // seats won (201)
const holdsRejected = new Counter('holds_rejected');   // seat already taken (409)
const holdsThrottled = new Counter('holds_throttled'); // rate-limited (429)

export const options = {
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    flash_sale: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '120s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // fewer than 1% genuine (non-409/429) errors
    checks: ['rate==1.0'],          // every VU check AND every teardown invariant must pass
    // The sale sold the whole inventory and not one seat more. Above EXPECTED_SEATS means
    // two requests won the same seat; below means seats were lost (or throttled away).
    holds_won: [STRICT_SELLOUT ? `count==${EXPECTED_SEATS}` : `count<=${EXPECTED_SEATS}`],
  },
};

function login(username, password) {
  const res = http.post(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      grant_type: 'password',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      username: username,
      password: password,
    },
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  );
  if (res.status !== 200) {
    throw new Error(`Keycloak login failed for "${username}": ${res.status} ${res.body}`);
  }
  return res.json('access_token');
}

/** The `sub` claim — the identity the booking service stores in seats.held_by. */
function subjectOf(token) {
  const payload = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's'));
  return payload.sub;
}

function identity(username, password) {
  const token = login(username, password);
  return { username, token, sub: subjectOf(token) };
}

function eventIdOf(token) {
  const res = http.get(`${BASE_URL}/api/events/${EVENT_SLUG}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status !== 200) {
    throw new Error(`Event lookup failed for "${EVENT_SLUG}": ${res.status}`);
  }
  return res.json('id');
}

/** The service's own seat map, with the holder and the optimistic-lock version. */
function adminSeats(adminToken, eventId) {
  const res = http.get(`${BASE_URL}/api/admin/seats?eventId=${eventId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  if (res.status !== 200) {
    throw new Error(
      `Admin seat map failed (${res.status}). LOADTEST_ADMIN_USER must hold the `
      + `'admin' realm role -- grant it with scripts/grant-admin.sh and log in again.`);
  }
  return res.json();
}

export function setup() {
  const admin = identity(ADMIN_USERNAME, ADMIN_PASSWORD);
  const identities = POOL.map((name) => identity(name, PASSWORD));

  const eventId = eventIdOf(admin.token);
  const seats = adminSeats(admin.token, eventId);
  const available = seats.filter((s) => s.status === 'AVAILABLE');
  if (available.length !== EXPECTED_SEATS) {
    throw new Error(
      `Event "${EVENT_SLUG}" has ${available.length} AVAILABLE seats but EXPECTED_SEATS is `
      + `${EXPECTED_SEATS}. Reset the demo data, or pass -e EXPECTED_SEATS=${available.length}.`);
  }

  // Baseline optimistic-lock versions: winning a seat bumps it by exactly 1.
  const baseline = {};
  available.forEach((s) => { baseline[String(s.id)] = s.version; });

  console.log(`Flash sale on "${EVENT_SLUG}" (event ${eventId}): ${available.length} seats, `
    + `${VUS} VUs, ${ITERATIONS} attempts, ${identities.length} identit${identities.length === 1 ? 'y' : 'ies'}, `
    + `${SEATS_PER_HOLD} seat(s) per hold`);
  if (identities.length === 1) {
    console.warn('Single identity: the per-user rate limiter will throttle the storm and every '
      + 'winner is the same person. Seed a pool with ./seed-loadtest-users.sh and pass '
      + '-e LOADTEST_USER_COUNT=N.');
  }

  return {
    identities,
    adminToken: admin.token,
    eventId,
    seatIds: available.map((s) => s.id),
    baseline,
  };
}

export default function (data) {
  // Spread the pool over the VUs: each VU is one person for the whole run, so the
  // per-user rate limit and the held_by values behave like a real crowd.
  const me = data.identities[(__VU - 1) % data.identities.length];

  const seatIds = [];
  while (seatIds.length < SEATS_PER_HOLD) {
    const id = data.seatIds[Math.floor(Math.random() * data.seatIds.length)];
    if (!seatIds.includes(id)) seatIds.push(id);
  }

  const res = http.post(`${BASE_URL}/api/events/${EVENT_SLUG}/holds`, JSON.stringify({ seatIds }), {
    headers: { Authorization: `Bearer ${me.token}`, 'Content-Type': 'application/json' },
  });
  check(res, { 'hold is 201, 409 or 429': (r) => r.status === 201 || r.status === 409 || r.status === 429 });

  // All-or-nothing: a 201 means every requested seat flipped to HELD for this caller.
  if (res.status === 201) holdsWon.add(seatIds.length);
  else if (res.status === 409) holdsRejected.add(1);
  else if (res.status === 429) holdsThrottled.add(1);
}

export function teardown(data) {
  // Authoritative cross-check against the service's own view of the seat map.
  const seats = adminSeats(data.adminToken, data.eventId);
  const byId = {};
  seats.forEach((s) => { byId[String(s.id)] = s; });
  const subs = {};
  data.identities.forEach((i) => { subs[i.sub] = i.username; });

  let held = 0;
  let stillAvailable = 0;
  let unheldButClaimed = 0; // HELD with no holder recorded
  let strangers = 0;        // held by someone who isn't a load-test identity
  let doubleWritten = 0;    // version moved more than once == more than one winner
  let missing = 0;          // a targeted seat that is no longer in the map at all
  const holders = {};

  data.seatIds.forEach((id) => {
    const seat = byId[String(id)];
    if (!seat) {
      missing++;
      return;
    }
    if (seat.status === 'HELD') held++;
    if (seat.status === 'AVAILABLE') stillAvailable++;
    if (seat.status === 'HELD' && !seat.heldBy) unheldButClaimed++;
    if (seat.heldBy) {
      holders[seat.heldBy] = true;
      if (!subs[seat.heldBy]) strangers++;
    }
    if (seat.version - data.baseline[String(id)] > 1) doubleWritten++;
  });

  const distinctHolders = Object.keys(holders).length;
  console.log(`Invariant: ${held}/${data.seatIds.length} target seats HELD, ${stillAvailable} still `
    + `AVAILABLE, ${distinctHolders} distinct holder(s), ${doubleWritten} seat(s) written twice`);

  check(
    { held, stillAvailable, unheldButClaimed, strangers, doubleWritten, distinctHolders, missing },
    {
      'every targeted seat is still in the seat map': (x) => x.missing === 0,
      // No seat was won by two requests: winning is a single conditional UPDATE, so a
      // second winner would have bumped the row's version a second time.
      'no seat has more than one holder': (x) => x.doubleWritten === 0,
      'every held seat records its holder': (x) => x.unheldButClaimed === 0,
      'every holder is one of the load-test identities': (x) => x.strangers === 0,
      'the sale was spread across the identity pool': (x) => POOL.length === 1 || x.distinctHolders > 1,
      'every seat sold (held == inventory)': (x) => (STRICT_SELLOUT ? x.held === data.seatIds.length : x.held > 0),
      'no targeted seat left unsold': (x) => (STRICT_SELLOUT ? x.stillAvailable === 0 : true),
    },
  );
}
