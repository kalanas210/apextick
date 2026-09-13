# ApexTick Postman collection

Everything the booking-service serves, ready to import: 43 requests across auth, the catalog, holds, orders,
tickets, payments, admin, the gate and ops, with a sample body for every POST, PUT and PATCH.

| File | What it is |
| --- | --- |
| `ApexTick.postman_collection.json` | Collection (v2.1), folders `01 · Auth` … `09 · Ops` |
| `ApexTick-Local.postman_environment.json` | The `ApexTick Local` environment: base URLs, the token client, and the variables the test scripts fill in |

## Import

1. In Postman, create or pick a workspace.
2. **Import**, and drop both JSON files in.
3. Choose **ApexTick Local** in the environment selector, top right.

## Use

1. Set the environment's `username` and `password` to your account: one you registered, or a demo deployment's
   shared account, which its sign-in page prints. `clientSecret` is `LOADTEST_CLIENT_SECRET` from `.env`
   (`dev-loadtest-secret` locally).
2. `01 · Auth → Get token` logs in with the password grant on the confidential `apextick-loadtest` client and
   stores `{{accessToken}}`, which every request inherits as Bearer auth. Run it again when requests start
   answering 401.
3. `02 · Catalog → Event detail`, then `Seats for event`: their test scripts save `eventId`, `seatId1` and
   `seatId2`.
4. `03 · Holds → Hold seats` → `04 · Orders → Create order` → `Pay (mock card)`. The `4242 4242 4242 4242` body
   succeeds; `…0002` is declined and `…9995` has insufficient funds.
5. `05 · Tickets → Tickets for order` → `Ticket PDF` (use **Send and Download**), then `08 · Gate → Scan ticket`.

`07 · Admin` needs the `admin` realm role, and `08 · Gate` the `admin` or `scanner` role: grant one with
`scripts/grant-role.sh admin <user>` (or `scanner`), then get a new token. `07 · Admin → Create event` dates the
new event a month out, on sale from the day before, so it can be sold straight away. If port 8081 is taken, run
booking-service on another port and change `baseUrl`.
