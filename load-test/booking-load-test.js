import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// ApexTick flash-sale load + correctness test.
//
// A crowd of virtual users races to hold seats on a single event. Two outcomes are
// BOTH correct: 200 (you won the seat) and 409 (someone beat you to it). The seat row
// in Postgres is the synchronization point, so identity does not matter — every seat
// can flip AVAILABLE -> HELD exactly once. We prove that after the storm:
//   * every seat that started AVAILABLE is now HELD  (all sold, none lost), and
//   * no targeted seat is still AVAILABLE            (no seat sold twice / skipped).
//
// Auth is real: each run logs into Keycloak (password grant, on the confidential
// apextick-loadtest client -- the SPA's public client doesn't accept it) and calls
// the JWT-secured hold endpoint with a Bearer token, exactly like the app does.
//
// Credentials are required, never defaulted:
//   LOADTEST_USER, LOADTEST_PASSWORD   the account to log in as
//   LOADTEST_CLIENT_SECRET             apextick-loadtest's secret (from .env)
//
// Run:  k6 run -e LOADTEST_USER=... -e LOADTEST_PASSWORD=... -e LOADTEST_CLIENT_SECRET=... booking-load-test.js
// Tune: add -e VUS=200 -e ITERATIONS=5000 -e EVENT_SLUG=india-australia-semi-final

const REQUIRED = ['LOADTEST_USER', 'LOADTEST_PASSWORD', 'LOADTEST_CLIENT_SECRET'];
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
const EVENT_SLUG = __ENV.EVENT_SLUG || 'india-australia-semi-final';
const VUS = Number(__ENV.VUS || 200);
const ITERATIONS = Number(__ENV.ITERATIONS || 5000);

// 200 (won the seat) and 409 (already taken) are BOTH correct — don't count 409 as failed.
http.setResponseCallback(http.expectedStatuses(200, 409));

const holdsWon = new Counter('holds_won');           // got the seat (200)
const holdsRejected = new Counter('holds_rejected'); // seat already taken (409)

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
    http_req_failed: ['rate<0.01'], // fewer than 1% genuine (non-409) errors
    checks: ['rate==1.0'],          // every VU check AND the teardown invariant must pass
    holds_won: ['count>0'],         // the sale actually happened
  },
};

function login() {
  const res = http.post(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      grant_type: 'password',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      username: USERNAME,
      password: PASSWORD,
    },
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  );
  if (res.status !== 200) {
    throw new Error(`Keycloak login failed: ${res.status} ${res.body}`);
  }
  return res.json('access_token');
}

function fetchSeats(token) {
  const res = http.get(`${BASE_URL}/api/events/${EVENT_SLUG}/seats`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status !== 200) {
    throw new Error(`Seat fetch failed for "${EVENT_SLUG}": ${res.status}`);
  }
  return res.json();
}

export function setup() {
  const token = login();
  const seats = fetchSeats(token);
  const seatIds = seats.filter((s) => s.status === 'AVAILABLE').map((s) => s.id);
  if (seatIds.length === 0) {
    throw new Error(`Event "${EVENT_SLUG}" has no AVAILABLE seats — reset the demo data and retry.`);
  }
  console.log(`Flash sale on "${EVENT_SLUG}": ${seatIds.length} seats, ${VUS} VUs, ${ITERATIONS} attempts`);
  return { token, seatIds, initialAvailable: seatIds.length };
}

export default function (data) {
  const seatId = data.seatIds[Math.floor(Math.random() * data.seatIds.length)];
  const res = http.post(`${BASE_URL}/api/seats/${seatId}/hold`, null, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  check(res, { 'hold is 200 or 409': (r) => r.status === 200 || r.status === 409 });
  if (res.status === 200) holdsWon.add(1);
  else if (res.status === 409) holdsRejected.add(1);
}

export function teardown(data) {
  // Authoritative cross-check against the service's own view of the seat map.
  const seats = fetchSeats(data.token);
  const target = new Set(data.seatIds);
  const held = seats.filter((s) => target.has(s.id) && s.status === 'HELD').length;
  const stillAvailable = seats.filter((s) => target.has(s.id) && s.status === 'AVAILABLE').length;
  console.log(`Invariant: ${held}/${data.initialAvailable} target seats HELD, ${stillAvailable} still AVAILABLE`);
  check(
    { held, stillAvailable },
    {
      'every seat sold exactly once (held == initial available)': (x) => x.held === data.initialAvailable,
      'no targeted seat left unsold': (x) => x.stillAvailable === 0,
    },
  );
}
