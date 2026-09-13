import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// ApexTick flash-sale load + correctness test.
//
// A crowd of virtual users races to hold seats on a single event through the SAME
// endpoint the UI uses: POST /api/events/{slug}/holds (multi-seat, all-or-nothing,
// rate-limited per user, capped per user). Four outcomes are all correct:
//   201 you won the seats            409 someone beat you to one of them
//   422 TOO_MANY_SEATS you are already holding your personal maximum
//   429 the limiter throttled you
//
// ---------------------------------------------------------------------------
// One virtual user = one person
// ---------------------------------------------------------------------------
// Every VU logs in as its OWN Keycloak account, and VUS is therefore capped to the
// size of the identity pool. That is not a detail, it is what makes the invariants
// below provable:
//   * the seat cap (app.hold.max-seats) and the hold rate limiter are per SUBJECT.
//     Ten VUs sharing one login would share one budget and would not behave like
//     ten buyers.
//   * a VU never asks for a seat it already holds, so every 201 it gets is a set of
//     seats won for the first time -- `holds_won` counts distinct seats, and a row
//     whose optimistic-lock `version` moved twice really was written by two
//     different holders. If VUs shared a login that would no longer follow: the
//     hold UPDATE is idempotent for its own holder (SeatRepository.holdSeatsAtomically
//     also matches rows where heldBy = :sub, and still does version = version + 1),
//     so a second VU on the same account would get a legitimate 201 and a legitimate
//     second version bump on a seat its twin already owned -- and the double-booking
//     detector would cry wolf.
//
// What this proves (and how):
//   * holds_won == the seats the pool can win   every seat was won exactly once. A
//     second winner for a seat would push the counter above the inventory.
//   * teardown reads the seat map back through the admin API and checks each targeted
//     seat: it ends HELD, it has a holder, that holder is one of the load-test
//     identities, no identity exceeded the per-person cap, and the seat's `version`
//     moved by exactly 1 since setup. A seat two people both "won" would have been
//     written twice and show +2 -- that is the double-booking detector. Final status
//     alone cannot see it.
//
// Auth is real: every identity logs into Keycloak (password grant on the confidential
// apextick-loadtest client -- the SPA's public client doesn't accept it) and calls the
// JWT-secured endpoints with a Bearer token, exactly like the app does.
//
// ---------------------------------------------------------------------------
// How big does the pool have to be?
// ---------------------------------------------------------------------------
// booking-service caps how many seats ONE PERSON may hold on ONE EVENT at
// app.hold.max-seats (APP_HOLD_MAX_SEATS, default 8) -- per person, not per request.
// So N identities can win at most N * max-seats seats, full stop. Selling out a
// 300-seat event needs ceil(300 / 8) = 38 accounts; anything less is arithmetically
// incapable of a sell-out no matter how many iterations you throw at it.
//
// This script does not pretend otherwise. It computes what the pool can win and
// asserts exactly that:
//   * pool * cap >= inventory  -> strict sell-out run: holds_won == inventory,
//     every seat HELD, nothing left AVAILABLE.
//   * pool * cap <  inventory  -> capped run: it still proves no double-booking, no
//     stranger holding a seat and no identity over the cap, and it says out loud
//     that it is not a sell-out proof and how many accounts one would need.
// A request from an identity that is already at its cap comes back 422 TOO_MANY_SEATS.
// That is the cap working, so it is counted in `holds_capped` and accepted by the
// checks -- not treated as a failed request.
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
// 1. Seed the identity pool once (creates loadtest-01 .. loadtest-NN in the realm).
//    Seed at least as many as you want VUs -- the headline run uses 200:
//      ./seed-loadtest-users.sh 200
// 2. Run:
//      export LOADTEST_USER=kalana LOADTEST_PASSWORD=...
//      export LOADTEST_ADMIN_USER=kalana LOADTEST_ADMIN_PASSWORD=...
//      export LOADTEST_CLIENT_SECRET=$(sed -n 's/^LOADTEST_CLIENT_SECRET=//p' ../.env)
//      k6 run -e LOADTEST_USER_COUNT=200 booking-load-test.js
//    The storm itself takes seconds, but setup logs all 200 accounts in first and
//    Keycloak hashes passwords slowly, so expect a quiet minute before the first hold
//    (options.setupTimeout is sized from the pool for exactly that reason).
//
// Why 200 and not 38: the hold endpoint is rate-limited per subject (30/min by
// default) -- a deliberate abuse control with its own test (booking-service
// .../platform/RateLimitHttpTest.java). The 5000 attempts land in about three seconds,
// i.e. inside one window, so each account effectively gets 30 of them. ITERATIONS has
// to stay around 5000 because covering 300 seats with uniform random draws needs about
// 300 * ln(300) ~ 1700 attempts just to touch every seat, and 5000 / 30 ~ 167 accounts
// keeps every VU under the limiter. Setup warns when the pool is small enough to be
// throttled. The alternative is a small pool with the limiter lifted for the run:
//      ./seed-loadtest-users.sh 40
//      APP_RATE_LIMIT_HOLD_LIMIT=100000 docker compose up -d booking-service
//      k6 run -e LOADTEST_USER_COUNT=40 booking-load-test.js
// (docker-compose.yml declares APP_RATE_LIMIT_* and APP_HOLD_MAX_SEATS, so an override
// in the shell or .env actually reaches the container.)
//
// Tunables (-e NAME=value):
//   BASE_URL KEYCLOAK_URL REALM CLIENT_ID EVENT_SLUG VUS ITERATIONS
//   LOADTEST_USER_COUNT   identities in the pool (default 1 = LOADTEST_USER only)
//   LOADTEST_USER_PREFIX  pool username prefix (default loadtest-, matching the seeder)
//   EXPECTED_SEATS        the event's inventory (default 300, the seeded demo event);
//                         setup fails loudly if the event doesn't have exactly this
//                         many AVAILABLE seats, so the threshold can never be vacuous
//   HOLD_MAX_SEATS        the service's app.hold.max-seats (default 8). The sell-out
//                         arithmetic is built from it, so setup PROBES the running
//                         service for its real value and refuses to run on a mismatch
//   HOLD_RATE_LIMIT       the service's app.rate-limit.hold.limit (default 30); used
//                         only to warn when the run would be throttled
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
const ITERATIONS = Number(__ENV.ITERATIONS || 5000);
const EXPECTED_SEATS = Number(__ENV.EXPECTED_SEATS || 300);
const SEATS_PER_HOLD = Number(__ENV.SEATS_PER_HOLD || 1);
const USER_COUNT = Number(__ENV.LOADTEST_USER_COUNT || 1);
const USER_PREFIX = __ENV.LOADTEST_USER_PREFIX || 'loadtest-';

// app.hold.max-seats / app.rate-limit.hold.limit, as the service is configured. The
// thresholds below are derived from the cap, and k6 fixes `options` before setup()
// runs, so it has to be declared here -- setup() then probes the service and refuses
// to run if the declaration is wrong (see probeHoldCap).
const HOLD_MAX_SEATS = Number(__ENV.HOLD_MAX_SEATS || 8);
const HOLD_RATE_LIMIT = Number(__ENV.HOLD_RATE_LIMIT || 30);

if (!(SEATS_PER_HOLD >= 1 && SEATS_PER_HOLD <= HOLD_MAX_SEATS)) {
  throw new Error(
    `SEATS_PER_HOLD must be between 1 and ${HOLD_MAX_SEATS} (app.hold.max-seats), got ${SEATS_PER_HOLD}`);
}

// The pool: LOADTEST_USER alone, or loadtest-01..loadtest-NN seeded by
// seed-loadtest-users.sh (which gives them all LOADTEST_PASSWORD).
const POOL = USER_COUNT > 1
  ? Array.from({ length: USER_COUNT }, (_, i) => `${USER_PREFIX}${String(i + 1).padStart(2, '0')}`)
  : [USERNAME];

// One identity per VU (see the header): more VUs than accounts would make two VUs
// share a subject, and the double-booking detector depends on that never happening.
// (setup() says so out loud if the clamp bites -- this file is the init context, which
// k6 re-runs for every VU, so warning from here would print the same line N times.)
const REQUESTED_VUS = Number(__ENV.VUS || POOL.length);
const VUS = Math.min(REQUESTED_VUS, POOL.length);

// How many seats this pool is physically allowed to win, and whether that is a sell-out.
const POOL_CAPACITY = POOL.length * HOLD_MAX_SEATS;
const WINNABLE_SEATS = Math.min(EXPECTED_SEATS, POOL_CAPACITY);
const IDENTITIES_FOR_SELLOUT = Math.ceil(EXPECTED_SEATS / HOLD_MAX_SEATS);

// With one seat per request AND enough accounts to cover the inventory, the sale is a
// clean partition: every seat can be won exactly once, so "won == inventory" and
// "nothing left AVAILABLE" are exact. Otherwise the same invariants hold as upper bounds.
const STRICT_SELLOUT = SEATS_PER_HOLD === 1 && POOL_CAPACITY >= EXPECTED_SEATS;

// 201 (won), 409 (already taken), 422 (this person is at their seat cap) and 429
// (throttled) are all correct server answers, as are the 200s from Keycloak and the
// admin seat map — don't count any of them as failed requests. A 422 that is NOT the
// cap still fails the per-iteration check below, so nothing is swept under the rug.
http.setResponseCallback(http.expectedStatuses(200, 201, 409, 422, 429));

const holdsWon = new Counter('holds_won');             // distinct seats won (201)
const holdsRejected = new Counter('holds_rejected');   // seat already taken (409)
const holdsCapped = new Counter('holds_capped');       // caller at app.hold.max-seats (422)
const holdsThrottled = new Counter('holds_throttled'); // rate-limited (429)

export const options = {
  // setup() logs the WHOLE pool in, one blocking password grant per identity, and
  // Keycloak hashes passwords deliberately slowly. k6 allows setup 60s by default, which
  // the 200-account headline run can walk straight through -- and it aborts with "setup()
  // execution timed out", which reads like a broken test rather than a slow login. Budget
  // 2s per account so a genuinely wedged Keycloak still fails instead of hanging.
  setupTimeout: `${Math.max(60, POOL.length * 2 + 30)}s`,
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
    http_req_failed: ['rate<0.01'], // fewer than 1% genuine (non-409/422/429) errors
    checks: ['rate==1.0'],          // every VU check AND every teardown invariant must pass
    // A VU never re-requests a seat it already holds, so holds_won counts DISTINCT
    // seats. Above the inventory would mean two requests won the same seat; above
    // POOL_CAPACITY would mean the per-person cap was bypassed. With enough accounts
    // to cover the inventory the sale must also be exact: every seat, once.
    holds_won: [STRICT_SELLOUT ? `count==${EXPECTED_SEATS}` : `count<=${WINNABLE_SEATS}`],
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

/** The RFC-7807 `code` of an error response, or null if the body isn't a problem. */
function problemCode(res) {
  try {
    return res.json('code');
  } catch (e) {
    return null;
  }
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

/**
 * Asks the service what app.hold.max-seats really is, so the sell-out arithmetic can
 * never be built on a stale assumption.
 *
 * The probe requests more seats than any plausible cap, using ids that cannot exist
 * (negative). HoldService checks the request size before it resolves the event or
 * touches a row, so an over-cap request answers 422 TOO_MANY_SEATS with the cap in the
 * problem body and changes nothing. If the cap were somehow larger than the probe, the
 * conditional UPDATE would match none of those ids and answer 409 -- still no writes.
 * It costs one token from this identity's hold rate-limit bucket.
 */
function probeHoldCap(token) {
  const size = Math.max(64, HOLD_MAX_SEATS + 1);
  const seatIds = Array.from({ length: size }, (_, i) => -(i + 1));
  const res = http.post(`${BASE_URL}/api/events/${EVENT_SLUG}/holds`, JSON.stringify({ seatIds }), {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  });
  if (res.status === 422 && problemCode(res) === 'TOO_MANY_SEATS') {
    const cap = res.json('maxSeats');
    if (typeof cap === 'number' && cap > 0) {
      return cap;
    }
  }
  if (res.status === 409) {
    // The size check let a 64-seat request through, so the real cap is at least that.
    throw new Error(
      `This service allows at least ${size} seats per person, but HOLD_MAX_SEATS is `
      + `${HOLD_MAX_SEATS}. Pass -e HOLD_MAX_SEATS=<app.hold.max-seats> so the sell-out `
      + `thresholds match the service.`);
  }
  console.warn(`Could not read app.hold.max-seats from the service (probe answered ${res.status}); `
    + `trusting HOLD_MAX_SEATS=${HOLD_MAX_SEATS}.`);
  return null;
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
  if (REQUESTED_VUS > POOL.length) {
    console.warn(`VUS=${REQUESTED_VUS} but the identity pool has ${POOL.length} account(s): running `
      + `${VUS} VU(s) so that no two virtual users share a login. Seed a bigger pool with `
      + `./seed-loadtest-users.sh ${REQUESTED_VUS} to run the full storm.`);
  }

  const admin = identity(ADMIN_USERNAME, ADMIN_PASSWORD);
  const identities = POOL.map((name) => identity(name, PASSWORD));

  // The thresholds are already fixed from HOLD_MAX_SEATS; make sure that is the truth.
  const actualCap = probeHoldCap(identities[0].token);
  if (actualCap !== null && actualCap !== HOLD_MAX_SEATS) {
    throw new Error(
      `app.hold.max-seats is ${actualCap} on this service but the run was planned with `
      + `HOLD_MAX_SEATS=${HOLD_MAX_SEATS}. Re-run with -e HOLD_MAX_SEATS=${actualCap} `
      + `(the sell-out thresholds are derived from it).`);
  }

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
    + `${VUS} VU(s) = ${identities.length} identit${identities.length === 1 ? 'y' : 'ies'}, `
    + `${ITERATIONS} attempts, ${SEATS_PER_HOLD} seat(s) per hold, `
    + `cap ${HOLD_MAX_SEATS} seat(s) per person`);

  if (STRICT_SELLOUT) {
    console.log(`Sell-out run: ${POOL.length} x ${HOLD_MAX_SEATS} = ${POOL_CAPACITY} >= `
      + `${EXPECTED_SEATS} seats, so every seat must be won exactly once.`);
  } else if (POOL_CAPACITY < EXPECTED_SEATS) {
    console.warn(`Capped run, NOT a sell-out proof: ${POOL.length} identit`
      + `${POOL.length === 1 ? 'y' : 'ies'} x ${HOLD_MAX_SEATS} seats = ${POOL_CAPACITY} of `
      + `${EXPECTED_SEATS} seats. Everything above the cap answers 422 TOO_MANY_SEATS (correctly). `
      + `For a sell-out seed ${IDENTITIES_FOR_SELLOUT} accounts `
      + `(./seed-loadtest-users.sh ${IDENTITIES_FOR_SELLOUT}) or raise APP_HOLD_MAX_SEATS.`);
  }

  // >= not >: the cap probe above has already spent one of this window's tokens.
  const perIdentity = Math.ceil(ITERATIONS / Math.max(VUS, 1));
  if (perIdentity >= HOLD_RATE_LIMIT) {
    console.warn(`Each identity will issue about ${perIdentity} holds against a limit of `
      + `${HOLD_RATE_LIMIT}/window, so much of the storm will come back 429 and holds_won will `
      + `fall short. Either seed more accounts, or raise the limiter for the run: `
      + `APP_RATE_LIMIT_HOLD_LIMIT=100000 docker compose up -d booking-service`);
  }

  return {
    identities,
    adminToken: admin.token,
    eventId,
    seatIds: available.map((s) => s.id),
    baseline,
  };
}

// Per-VU state. k6 gives every VU its own JS runtime, so this is private to one VU --
// and because VUS <= POOL.length, private to one identity. It is what lets the test
// distinguish "I am re-posting my own seat" (which the service legitimately accepts and
// which would bump the row's version again) from "someone else won my seat".
const myHeld = {};

/** SEATS_PER_HOLD distinct seat ids this VU does not already hold, or [] if it holds them all. */
function drawSeats(seatIds) {
  const picked = [];
  // Bounded: the draw is uniform over the whole inventory, so this only spins when a VU
  // holds nearly everything (possible only with a huge APP_HOLD_MAX_SEATS).
  for (let tries = 0; tries < 200 && picked.length < SEATS_PER_HOLD; tries++) {
    const id = seatIds[Math.floor(Math.random() * seatIds.length)];
    if (!myHeld[id] && picked.indexOf(id) === -1) picked.push(id);
  }
  return picked.length === SEATS_PER_HOLD ? picked : [];
}

export default function (data) {
  // One identity per VU for the whole run, so the per-user rate limit, the per-user seat
  // cap and the held_by values all behave like a real crowd of buyers.
  const me = data.identities[(__VU - 1) % data.identities.length];

  const seatIds = drawSeats(data.seatIds);
  if (seatIds.length === 0) return; // this VU already holds everything it could ask for

  const res = http.post(`${BASE_URL}/api/events/${EVENT_SLUG}/holds`, JSON.stringify({ seatIds }), {
    headers: { Authorization: `Bearer ${me.token}`, 'Content-Type': 'application/json' },
  });

  // 422 is only a correct answer when it is the per-person seat cap; any other 422
  // (a malformed request, say) still fails the run.
  const capped = res.status === 422 && problemCode(res) === 'TOO_MANY_SEATS';
  check(res, {
    'hold answered 201, 409, 422 TOO_MANY_SEATS or 429': (r) =>
      r.status === 201 || r.status === 409 || r.status === 429 || capped,
  });

  if (res.status === 201) {
    // All-or-nothing: a 201 means every requested seat flipped to HELD for this caller.
    // drawSeats never offers a seat this VU already holds, so these are all first wins.
    seatIds.forEach((id) => { myHeld[id] = true; });
    holdsWon.add(seatIds.length);
  } else if (res.status === 409) holdsRejected.add(1);
  else if (capped) holdsCapped.add(1);
  else if (res.status === 429) holdsThrottled.add(1);
  else {
    // Transport error or an answer we don't understand (status 0 on a timeout, a 5xx):
    // the hold may well have committed. The check above already failed the run, but
    // retire these seats anyway so a later re-draw can't re-post a hold we do own and
    // bump the row's version a second time -- the double-booking detector must only
    // ever fire on a real second holder.
    seatIds.forEach((id) => { myHeld[id] = true; });
  }
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
  let doubleWritten = 0;    // version moved more than once == more than one holder wrote it
  let missing = 0;          // a targeted seat that is no longer in the map at all
  const seatsPerHolder = {};

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
      seatsPerHolder[seat.heldBy] = (seatsPerHolder[seat.heldBy] || 0) + 1;
      if (!subs[seat.heldBy]) strangers++;
    }
    // Winning a seat is one conditional UPDATE that bumps `version` by 1. The same
    // person re-posting a seat they already hold would bump it again -- which is why
    // a VU never draws its own seats (see the header): with that guaranteed, a +2 can
    // only mean two different holders wrote the row.
    if (seat.version - data.baseline[String(id)] > 1) doubleWritten++;
  });

  const holders = Object.keys(seatsPerHolder);
  const overCap = holders.filter((sub) => seatsPerHolder[sub] > HOLD_MAX_SEATS).length;
  const distinctHolders = holders.length;

  console.log(`Invariant: ${held}/${data.seatIds.length} target seats HELD (${WINNABLE_SEATS} `
    + `winnable by this pool), ${stillAvailable} still AVAILABLE, ${distinctHolders} distinct `
    + `holder(s), ${overCap} over the ${HOLD_MAX_SEATS}-seat cap, ${doubleWritten} seat(s) written twice`);

  check(
    { held, stillAvailable, unheldButClaimed, strangers, doubleWritten, distinctHolders, missing, overCap },
    {
      'every targeted seat is still in the seat map': (x) => x.missing === 0,
      // No seat was won by two different people: winning is a single conditional UPDATE,
      // so a second holder would have bumped the row's version a second time.
      'no seat was won by two different holders': (x) => x.doubleWritten === 0,
      'every held seat records its holder': (x) => x.unheldButClaimed === 0,
      'every holder is one of the load-test identities': (x) => x.strangers === 0,
      'no identity holds more than the per-person cap': (x) => x.overCap === 0,
      'the sale was spread across the identity pool': (x) => POOL.length === 1 || x.distinctHolders > 1,
      'no more seats sold than the pool could win': (x) => x.held <= WINNABLE_SEATS,
      'every seat sold (held == inventory)': (x) => (STRICT_SELLOUT ? x.held === data.seatIds.length : x.held > 0),
      'no targeted seat left unsold': (x) => (STRICT_SELLOUT ? x.stillAvailable === 0 : true),
    },
  );
}
