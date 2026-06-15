import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 200 (won the seat) and 409 (seat already taken) are BOTH correct outcomes —
// tell k6 not to treat 409 as a failed request.
http.setResponseCallback(http.expectedStatuses(200, 409));

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const SEAT_COUNT = Number(__ENV.SEAT_COUNT || 200);
const FIRST_SEAT_ID = Number(__ENV.FIRST_SEAT_ID || 1);

const holdsWon = new Counter('holds_won');           // got the seat (200)
const holdsRejected = new Counter('holds_rejected'); // seat already taken (409)

export const options = {
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    flash_sale: {
      executor: 'shared-iterations',
      vus: 200,            // 200 users hitting at the same instant
      iterations: 5000,    // 5000 booking attempts in total
      maxDuration: '120s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'], // fewer than 1% genuine errors
  },
};

export default function () {
  const seatId = FIRST_SEAT_ID + Math.floor(Math.random() * SEAT_COUNT);

  const res = http.post(
    `${BASE_URL}/api/seats/${seatId}/hold`,
    JSON.stringify({ userId: `vu-${__VU}-iter-${__ITER}` }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(res, {
    'status is 200 or 409': (r) => r.status === 200 || r.status === 409,
  });

  if (res.status === 200) holdsWon.add(1);
  else if (res.status === 409) holdsRejected.add(1);
}