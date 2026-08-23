# WSO2 API Manager

ApexTick can run with **WSO2 API Manager 4.5.0** in front of the booking service.
It is an opt-in compose profile, not part of the default stack — the API is
perfectly usable without it, and the gateway needs about 4 GB of memory.

## Why a gateway at all

The booking service already validates JWTs and rate-limits holds in Redis. The
gateway earns its place by moving two of those concerns to the edge:

- **Rejecting bad traffic before it costs anything.** An unauthenticated request
  dies at the gateway; the service never opens a database connection for it.
- **Shedding a burst.** During a flash sale the interesting failure is not one
  slow request, it is ten thousand at once. A throttling policy on the seat-hold
  operation caps what reaches the service, and the callers that lose get a clean
  `429` instead of a timeout.

It also gives the API a published contract, a developer portal and a subscription
model — the things you actually want when other teams start calling you.

## Running it

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
scripts/wso2/setup.sh                # key manager, throttling policy, publish
scripts/wso2/smoke-test.sh           # what the gateway does with each kind of caller
```

| Surface | URL | Notes |
| --- | --- | --- |
| Gateway (http) | http://localhost:8280/api | what clients call |
| Gateway (https) | https://localhost:8243/api | self-signed certificate |
| Publisher | https://localhost:9443/publisher | `admin` / `admin` |
| DevPortal | https://localhost:9443/devportal | subscriptions, try-it console |
| Admin | https://localhost:9443/admin | key managers, throttling policies |

The portals are **never published in production**. `docker-compose.prod.yml`
exposes only Caddy; reach 9443 over an SSH tunnel if you need it.

## What `setup.sh` configures

1. **A REST client** via dynamic client registration, then an admin token — so
   every later step is a documented API call rather than a click.
2. **Keycloak as a key manager.** The gateway is told to trust the same realm the
   SPA already logs into, validating token signatures itself against the realm's
   JWKS (`self_validate_jwt`) instead of calling an introspection endpoint per
   request. The consumer key is read from the `azp` claim.
3. **A `ApexTickHoldBurst` throttling policy** — 10 requests per second per
   subscriber — applied to the seat-hold operations only. Everything else is
   `Unlimited`; the gateway is not trying to be the rate limiter for reads.
4. **The API itself**, imported from `wso2/apextick-api/openapi.json` (generated
   from the service's own springdoc output), with `/api` as its context so the
   gateway path matches the service path and nothing downstream has to change.
5. **A revision deployed to the gateway** and the API moved to `PUBLISHED`.
6. **A DevPortal application** subscribed to the API.

## Putting it in the request path

The gateway is not in the path by default. To route through it, point Caddy at
the gateway instead of the service:

```bash
API_UPSTREAM=wso2am:8280 docker compose -f docker-compose.prod.yml up -d caddy
```

For local development, point the frontend at it:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8280/api npm run dev
```

**The WebSocket stays on a direct route.** `/api/ws` is a long-lived upgraded
connection carrying STOMP frames; there is nothing an HTTP API gateway can
usefully police there, and putting one in the middle only adds a hop that can
drop the connection. Caddy handles `/api/ws*` before the gateway rule.

## What actually works today

Verified against WSO2 API Manager 4.5.0 with `scripts/wso2/smoke-test.sh`:

| Behaviour | Result |
| --- | --- |
| Unauthenticated request | `401` · `900902 Missing Credentials` — the backend is never dialled |
| Garbage bearer token | rejected · `900901` |
| Keycloak-issued access token | **validated** by the Keycloak key manager, then `403` · `900908 API Subscription validation failed` |
| API published, revision deployed | yes |
| `ApexTickHoldBurst` policy created and attached to the hold operations | yes |

So the gateway does the first job it was brought in for — it authenticates at the
edge — and the API, its throttling policy and the Keycloak key manager are all
configured from version control. The piece that does not work end to end is
subscription validation, and it is worth being precise about why.

## Known limitations

**Subscription validation with Keycloak tokens.** WSO2 will not let a token
through until the token's consumer key resolves to an application subscribed to
the API. Mapping the SPA's client (`apextick-web`) onto the DevPortal application
means calling `POST /applications/{id}/map-keys`, and the bundled Keycloak
connector (`keycloak.key.manager_2.1.1`) implements that by reading the client
back out of Keycloak's dynamic client registration endpoint:

```
feign.FeignException$Unauthorized: [401 Unauthorized] during [GET] to
[http://keycloak:8080/realms/apextick/clients-registrations/default/apextick-web]
[DCRClient#getApplication(String)]: {"error":"invalid_token","error_description":"Failed decode token"}
```

Keycloak 26 rejects the credential the connector presents there, so the mapping
never completes and every SPA token stops at `900908`. Note what this is *not*:
the token itself is fine. Reaching `900908` at all proves the gateway fetched the
realm's JWKS, verified the signature, matched the issuer and read the consumer
key out of `azp`. Only the application lookup fails.

Closing it properly means either an initial-access-token flow for Keycloak's DCR
endpoint or a newer connector build — worth doing before anyone relies on the
gateway, and deliberately not faked here.

**Why the SPA is not routed through the gateway by default.** Because of the
above. `API_UPSTREAM` is left pointing at the booking service, so the application
works; flipping it to `wso2am:8280` is a one-line change once the mapping is
fixed. Shipping the switch flipped would have meant shipping a broken checkout.

## Operational notes

- **H2, single node.** The bundled database is fine for a demo and wrong for
  production, where you would point `[database.apim_db]` at PostgreSQL and run
  the gateway, traffic manager and control plane separately.
- **Memory.** 4 GB for the container. On a laptop already running the rest of the
  stack this is the piece most likely to be the problem; lower it with
  `WSO2_MEM_LIMIT` and `WSO2_JVM_MEM_OPTS`, and expect a slower start.
- **First start is slow.** Three to four minutes before the health check passes.
  `setup.sh` waits for it rather than failing.
