# WSO2 API Manager

ApexTick runs **WSO2 API Manager 4.5.0** in front of the booking service. In
production (`docker-compose.prod.yml`) it is the default path — Caddy routes
`/api` through the gateway, not straight to `booking-service`. Locally it stays
an opt-in compose profile (the gateway needs ~4 GB of memory, more than most
laptops want to spend on every `docker compose up`).

## Why a gateway at all

The booking service already validates JWTs and rate-limits holds in Redis. The
gateway earns its place by moving two of those concerns to the edge:

- **Rejecting bad traffic before it costs anything.** An unauthenticated request
  to anything that needs a caller dies at the gateway; the service never opens
  a database connection for it. (The few routes the service itself serves to
  anyone pass straight through — see [Public operations](#public-operations).)
- **Shedding a burst.** During a flash sale the interesting failure is not one
  slow request, it is ten thousand at once. A throttling policy on the seat-hold
  operation caps what reaches the service, and the callers that lose get a clean
  `429` instead of a timeout. (This specific piece is currently broken — see
  Known limitations.)

It also gives the API a published contract, a developer portal and a subscription
model — the things you actually want when other teams start calling you.

## Running it locally

```bash
docker compose --profile wso2 up -d
```

The container takes three to four minutes to become healthy the first time.
Watch it with `docker compose logs -f wso2am`, or wait for:

```bash
docker inspect --format '{{.State.Health.Status}}' apextick-wso2am
```

Then configure it — this is scripted so the gateway's setup lives in version
control rather than in a browser session:

```bash
scripts/wso2/refresh-openapi.sh      # export the contract from booking-service
scripts/wso2/setup.sh                # key manager, throttling policy, publish, map-keys
scripts/wso2/smoke-test.sh           # what the gateway does with each kind of caller
```

`setup.sh` is fully idempotent and needs no manual follow-up — a fresh
`docker compose --profile wso2 up -d && scripts/wso2/setup.sh` reaches a working,
authenticated, correctly-routed gateway on its own.

It needs one secret: `WSO2_KM_CLIENT_SECRET`, the client secret of the
`apextick-wso2-km` service account. The realm import only carries a
`${WSO2_KM_CLIENT_SECRET}` placeholder that Keycloak fills from its own
environment, so the same value has to be in `.env` for compose (which hands it
to Keycloak) and for `setup.sh` (which hands it to WSO2). The script reads
`.env` itself, and stops before touching the gateway if the value is missing
or Keycloak doesn't accept it. Re-running it re-applies the key-manager
registration, which is how a rotated secret reaches the gateway — see
[keycloak/README.md](../keycloak/README.md) for rotating it on a realm that
already exists.

`smoke-test.sh` logs a real user in with the password grant on the
confidential `apextick-loadtest` client. It takes `LOADTEST_USER`,
`LOADTEST_PASSWORD` and `LOADTEST_CLIENT_SECRET` from the environment or
`.env`, has no built-in credentials, and stops if any is missing:

```bash
LOADTEST_USER=kalana LOADTEST_PASSWORD=12345 scripts/wso2/smoke-test.sh
```

| Surface | URL | Notes |
| --- | --- | --- |
| Gateway (http) | http://localhost:8280/api | what clients call |
| Gateway (https) | https://localhost:8243/api | self-signed certificate |
| Publisher | https://localhost:9443/publisher | `admin` / `WSO2_ADMIN_PASSWORD` from `.env` (`admin` if unset) |
| DevPortal | https://localhost:9443/devportal | subscriptions, try-it console |
| Admin | https://localhost:9443/admin | key managers, throttling policies |

The portals are **never published in production** — only loopback
(`127.0.0.1:9443`), reachable over an SSH tunnel, e.g.
`ssh -L 9443:localhost:9443 ubuntu@<server-ip>`.

## What `setup.sh` configures

1. **A REST client** via dynamic client registration, then an admin token — so
   every later step is a documented API call rather than a click.
2. **Keycloak as a key manager.** The gateway is told to trust the same realm the
   SPA already logs into, validating token signatures itself against the realm's
   JWKS (`self_validate_jwt`) as well as calling introspection for per-request
   validation. The consumer key is read from the `azp` claim.
3. **A `ApexTickHoldBurst` throttling policy** — 10 requests per second per
   subscriber — applied to the seat-hold operations only. Everything else is
   `Unlimited`; the gateway is not trying to be the rate limiter for reads.
4. **The API itself**, imported from `wso2/apextick-api/openapi.json` (generated
   from the service's own springdoc output, with the `/api` prefix stripped from
   every operation path — see below for why), with `/api` as the gateway's own
   context. On a gateway where it already exists, the definition is replaced
   with the committed one, so a regenerated contract takes effect on the next
   run.
5. **A revision deployed to the gateway** and the API moved to `PUBLISHED`.
6. **A DevPortal application** subscribed to the API.
7. **The SPA's Keycloak client (`apextick-web`) mapped onto that application**
   (`POST /applications/{id}/map-keys`) — a bring-your-own-key mapping, since
   `apextick-web` is a client the SPA already logs into directly, not one WSO2
   minted for itself. This is the step that used to fail outright (see the fix
   history below) and is now automatic.
8. **The same for `apextick-loadtest`**, on a second application
   (`ApexTickLoadTest`). k6 and `smoke-test.sh` log in through that
   confidential client — `apextick-web` no longer accepts the password grant —
   so their tokens carry `azp=apextick-loadtest` and need a subscription of
   their own. An application holds one production key per key manager, hence
   two. Both are mapped by client id only: the gateway validates tokens and
   never needs a client's secret, and WSO2's Keycloak connector rejects any
   non-blank `consumerSecret` as "wrong for the given consumer key" (its client
   lookup doesn't return Keycloak's secret to compare against).

## Putting it in the request path

**Production already does this by default** — `docker-compose.prod.yml`'s
`caddy` service defaults `API_UPSTREAM` to `wso2am:8280`. To bypass the gateway
temporarily, point it back at the service directly:

```bash
API_UPSTREAM=booking-service:8081 docker compose -f docker-compose.prod.yml up -d caddy
```

Configure and check it **on the server itself**, from the repo checkout the
stack runs from:

```bash
scripts/wso2/setup.sh
LOADTEST_USER=... LOADTEST_PASSWORD=... scripts/wso2/smoke-test.sh
```

Both read `.env` (`WSO2_ADMIN_PASSWORD`, the client secrets, `SERVER_IP`) and
ask the running Keycloak container for its `KC_HOSTNAME`. With one set — as
`docker-compose.prod.yml` does, `https://$SERVER_IP.nip.io` — `setup.sh`
registers the key manager with issuer `https://$SERVER_IP.nip.io/realms/apextick`
(the `iss` production tokens actually carry) and `smoke-test.sh` goes through
the public origin, `https://$SERVER_IP.nip.io/api`, because production publishes
no gateway port of its own. Both stop before doing anything if `.env`'s
`SERVER_IP` and Keycloak's `KC_HOSTNAME` disagree (a new Elastic IP with a
Keycloak that was never recreated, say), and `setup.sh` refuses a
`WSO2_KC_ISSUER` override on any other host — a key manager with the wrong
issuer rejects every token. `GATEWAY`, `KEYCLOAK` and `WSO2_KC_ISSUER` still
override everything.

**Locally**, the gateway is opt-in and the frontend talks to `booking-service`
directly unless told otherwise:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8280 npm run dev
```

No `/api` on the end: `NEXT_PUBLIC_API_URL` is the origin every call is
relative to, and the calls already start with `/api` — which is exactly the
gateway's context. With `.../api` there, requests go to `/api/api/...`, which
no gateway resource matches. The same variable also builds the live seat map's
WebSocket URL (`/api/ws`), and the gateway carries no WebSocket API, so seat
flips stop arriving live in this mode; the REST calls, holds and checkout are
unaffected. (Production is unaffected too: there Caddy sends `/api/ws` straight
to `booking-service`.)

`setup.sh`'s defaults (issuer `http://localhost:8180/realms/apextick`,
matching `apextick-web`'s existing redirect URI and booking-service's own
`JWT_ISSUER_URI` default) are already enough for a real browser login through
the gateway at `http://localhost:3000` — nothing else to configure. Verified:
none of the gateway's own validation steps (self-validated JWT signature,
introspection) ever need to dial the token's `iss` URL directly, only the
pre-registered, always-internally-reachable JWKS/introspection endpoints — so
`localhost` inside the `wso2am` container never actually needing to mean "the
host machine" turned out not to matter here.

If you're accessing the app from somewhere other than `localhost` — another
device on the LAN, a hostname Keycloak's redirect URIs don't already list —
`docker-compose.yml`'s `wso2am` service does carry
`extra_hosts: host.docker.internal:host-gateway` and `apextick-web` allows
`http://host.docker.internal:3000/*`, so overriding `WSO2_KC_ISSUER` and
booking-service's `JWT_ISSUER_URI` to `host.docker.internal` and browsing via
that hostname instead is there as a fallback. Production needs none of this —
Keycloak there has a fixed `KC_HOSTNAME` (the real `https://<server-ip>.nip.io`
domain), reachable like anything else on the internet, and `setup.sh` derives
the issuer from it as described above.

**The WebSocket stays on a direct route.** `/api/ws` is a long-lived upgraded
connection carrying STOMP frames; there is nothing an HTTP API gateway can
usefully police there, and putting one in the middle only adds a hop that can
drop the connection. Caddy handles `/api/ws*` before the gateway rule.

## Public operations

A few routes are public in booking-service itself (`permitAll()` in
`SecurityConfig`): the catalogue reads (`GET /events/**`, `GET /series/**`),
`GET /payments/config`, and Stripe's webhook, which authenticates with its own
signature rather than a bearer token. The SPA calls the catalogue and payment
config without a token even when signed in, so the gateway must not demand one
either — otherwise the event page, seat map and checkout get `401` in
production, and with Stripe enabled paid orders never confirm.

`scripts/wso2/normalise-openapi.py` marks exactly those operations with
`x-auth-type: None`, the per-resource switch WSO2's OpenAPI importer reads
(it ignores the standard `security: []`, which is set as well). The list is one
commented table in that script, mirroring `SecurityConfig`; a rule that stops
matching any operation fails the script rather than silently dropping out.
`GET /events/{idOrSlug}/holds/me` is deliberately left protected: the service's
`/api/events/**` glob covers it, but it answers for the caller.
`smoke-test.sh` checks both sides: an anonymous catalogue read gets `200`, and
a badly-signed webhook gets booking-service's own `400`, not the gateway's
`401`.

## What actually works today

Verified against WSO2 API Manager 4.5.0 with a real, browser-shaped token
(issued against a host the gateway container can itself reach — see above),
including from a **from-scratch stack** (`docker compose rm -f keycloak wso2am
booking-service` + a wiped `apextick_wso2` volume, so this is what the
committed config alone produces, not hand-patched state):

| Behaviour | Result |
| --- | --- |
| Unauthenticated request to a protected route | `401` · `900902 Missing Credentials` — the backend is never dialled |
| Garbage bearer token | rejected · `900901` |
| Keycloak-issued access token | validated, subscription validated, request reaches the backend · `200` |
| Same token reused repeatedly | consistently `200` (was flaky before the cache fix below) |
| Backend response | real data from booking-service, correctly routed |
| A 50-VU / 500-iteration k6 flash-sale run through the gateway | `224` holds won, `276` correctly rejected as already-taken, `0%` `http_req_failed` |

## Fix history — four distinct bugs, not one

Earlier revisions of this doc described subscription validation as an
unsolved limitation, blamed on either insufficient service-account
permissions or a Keycloak-version-incompatible connector build. Neither
theory was right. Diagnosed instead by MITM'ing the gateway's Keycloak-bound
traffic through a throwaway `socat -v` proxy (wired in as the registered key
manager's own endpoints via the Admin API, temporary, never committed) and by
reading Keycloak's own audit log — not by guessing at connector internals.

1. **`scope=default` doesn't exist.** The connector requests its own admin
   token with `grant_type=client_credentials&scope=default`. Keycloak has no
   built-in scope literally named `"default"`, so that 400s with
   `invalid_scope`, and the connector silently sends the literal string
   `Authorization: Bearer null` to the DCR read that maps `apextick-web` —
   which is the exact `401 Failed decode token` this doc used to document as
   unsolved. **Fix:** a `default` client scope in the realm, optional on
   `apextick-wso2-km`.
2. **Keycloak 26 requires the introspecting client in `aud`.** Once map-keys
   was unblocked, per-request validation calls Keycloak's introspection
   endpoint, which refused to report any token active because the
   introspecting client (`apextick-wso2-km`) never appeared in the token's
   `aud` claim — confirmed straight from Keycloak's own
   `INTROSPECT_TOKEN_ERROR` audit event. **Fix:** a `wso2-audience` client
   scope carrying an `oidc-audience-mapper`, default on both `apextick-web`
   and `apextick-wso2-km`.
3. **The gateway wasn't forwarding the original bearer token.**
   `[apim.oauth_config] enable_outbound_auth_header` defaults to `false`, so
   WSO2 authorized the request and then called booking-service without the
   original `Authorization` header — which booking-service's own,
   intentionally independent JWT check then rejected. **Fix:** enabled in
   `wso2/conf/deployment.toml`.
4. **The backend endpoint URL was missing the `/api` prefix.** Every
   controller in booking-service is mapped under `/api`
   (`@RequestMapping("/api/events")` etc.), but the OpenAPI operation paths
   registered with WSO2 have that prefix stripped (correctly — otherwise the
   gateway's own `/api` context would double up client-side). The backend URL
   needs the prefix put back for the upstream call, and never had it —
   `WSO2_BACKEND` defaulted to bare `http://booking-service:8081`, a 404 that
   was invisible until the three bugs above stopped masking it. **Fix:**
   `scripts/wso2/setup.sh` now defaults it to `.../8081/api`.

None of these needed broader service-account permissions than the original
three realm-management roles (`manage-clients`/`view-clients`/`view-realm`) —
the WSO2-docs-recommended broader grant (`view-users`/`query-users`/`manage-users`,
`directAccessGrantsEnabled`) was tried first on that hunch and reverted; it
changed nothing.

A fifth issue surfaced during verification, also fixed: a **gateway-side token
cache correctness bug** — a second request with the exact same still-valid
token intermittently got a different authorization outcome than the first.
Direct repeated introspection against Keycloak was 100% consistent, isolating
this to WSO2's own `[apim.cache.gateway_token]`, left at its default (enabled)
in the shipped config. Disabled.

## Known limitations

**Throttling does not actually throttle.** `ApexTickHoldBurst`'s Siddhi
execution plan fails to deploy on every gateway startup:

```
ERROR - PolicyUtil Error in deploying execution plan
org.wso2.carbon.event.processor.core.exception.ExecutionPlanConfigurationException: Couldn't parse execution plan: ...
```

with no chained cause logged. WSO2's own built-in resource-tier policies
(e.g. `10KPerMin`) deploy fine on the same node, so this looks like a
product-level incompatibility specific to *custom* advanced policies in this
WSO2AM 4.5.0 build, not something wrong in this repo's policy definition.
Confirmed under real load: a k6 run at ~400 req/s from a single subscriber —
40x the configured 10 req/s — returned zero `429`s. The policy is created,
attached to the right operations, and inert. Not yet root-caused further;
booking-service's own Redis-backed hold limiter is the only rate limiting
that's actually enforced right now, gateway or not.

## Operational notes

- **H2, single node.** The bundled database is fine here and wrong for a real
  production workload, where you'd point `[database.apim_db]` at PostgreSQL and
  run the gateway, traffic manager and control plane separately.
- **Memory.** Production runs on a `t3.large` (8 GB) specifically to fit this
  alongside everything else; `WSO2_MEM_LIMIT` there defaults to `3g`. Locally,
  4 GB (`WSO2_MEM_LIMIT`/`WSO2_JVM_MEM_OPTS`) is the piece most likely to be a
  problem on a laptop already running the rest of the stack.
- **First start is slow.** Three to four minutes before the health check passes
  on a fresh volume. `setup.sh` waits for it rather than failing.
