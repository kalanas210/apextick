# Keycloak realm

`import/apextick-realm.json` is imported by `--import-realm` the first
time Keycloak starts on an empty database. After that the realm lives in the
database, and editing this file changes nothing on that instance -- patch a
running realm through the Admin API instead (see `scripts/grant-role.sh`).

## Values that come from the environment

Keycloak substitutes `${VAR}` and `${VAR:default}` in the import file from the
Keycloak container's environment, so secrets and per-deployment URLs stay out
of git. The compose files pass these through from `.env`.

| Placeholder | Used for | Default in the realm |
| --- | --- | --- |
| `WSO2_KM_CLIENT_SECRET` | secret of `apextick-wso2-km`, the service account WSO2 uses to read and manage clients | none -- required |
| `LOADTEST_CLIENT_SECRET` | secret of `apextick-loadtest`, the only client that accepts the password grant | none -- required |
| `APP_WEB_URL` | the deployed frontend's origin, allowed as an `apextick-web` redirect URI (prod: `https://<SERVER_IP>.nip.io`) | `http://localhost:3000` |
| `DEV_PREVIEW_WEB_URL`, `DEV_DOCKER_WEB_URL` | two more `apextick-web` redirect URIs for local development: a preview server on :3005, and the frontend as seen from inside docker. Production sets both to its own origin, so it allows no localhost redirect | `http://localhost:3005`, `http://host.docker.internal:3000` |
| `DEMO_USER_EMAIL` | the seeded `kalana` account's address | `kalana@apextick.local` |
| `SMTP_*` | outgoing mail | the bundled Mailpit |

A placeholder with no default that is **not** set is not an error: Keycloak
imports the literal text (the client secret becomes the string
`${WSO2_KM_CLIENT_SECRET}`, which anyone reading this repo knows, and it
works). That is why `docker-compose.prod.yml` must refuse to start without the
two secrets, and why `scripts/wso2/setup.sh` refuses to use a value that looks
like a placeholder.

An **empty** value is worse still: Keycloak stores an empty secret and then
accepts an empty `client_secret` for that client. So never give the secrets an
empty default (`${WSO2_KM_CLIENT_SECRET:}` in the realm), and in compose use
the colon forms, `${VAR:?...}` or `${VAR:-...}`, which treat an empty `.env`
entry as unset -- a bare `${VAR}` passes the empty string through.

Generate a secret with `openssl rand -hex 32`.

## Hardening

- The SPA's client, `apextick-web`, is public and takes authorization code +
  PKCE only. The password grant (for k6, the gateway smoke test, curl) lives on
  the confidential `apextick-loadtest` client, so guessing passwords at the
  token endpoint needs a secret first. That includes Keycloak's own public
  `admin-cli`, which every realm gets with the password grant on: the import
  defines it with that grant off.
- The seeded `kalana` account has a fixed id, so a fresh import gives it the
  same `sub` and its bookings stay attached to it.
- `sslRequired: external` -- plain HTTP is accepted only from localhost and
  private addresses (local dev, the docker network, Caddy with
  `KC_PROXY_HEADERS`); a public client has to use HTTPS.
- Brute-force detection: 10 failures lock an account out for a minute, growing
  to at most 15 minutes. Temporary on purpose, so a stranger can't permanently
  lock the published demo account.
- Password policy: 8 to 128 characters, not the username or email. It applies
  to every password set after import -- registration, reset, the Admin API.
- The import defines the `admin` and `scanner` realm roles and grants them to
  nobody. `scanner` is for gate devices: it reaches `/api/gate/**` and nothing
  else. `scripts/grant-role.sh admin|scanner <user>` grants one on a running
  realm, creating the role first if that realm was imported before it existed.

The demo account's published password (`12345`, advertised on the sign-in
page) is shorter than that policy allows. Keycloak checks a plaintext seed
`value` against the policy too and would abort the whole import
(`invalidPasswordMinLengthMessage`), so it is seeded pre-hashed instead
(`secretData`/`credentialData`, the same shape a realm export produces), which
the policy has nothing to check against. Keycloak re-hashes it with the current
default algorithm at the first login. To regenerate it: PBKDF2WithHmacSHA512,
210000 iterations, a 16-byte random salt, a 512-bit key, both base64-encoded.

## Where the realm lives

Production Keycloak keeps the realm, its accounts and their sessions in the
`keycloak` database on the stack's Postgres, so recreating the container keeps
all of it. Local development still runs `start-dev`, whose database lives inside
the container: a recreate there re-imports this file and drops the accounts
registered since.

**A production stack still on `start-dev` moves onto Postgres** the first time
it is brought up with the current `docker-compose.prod.yml`. Keycloak starts on
an empty `keycloak` database and imports this file one last time: everything
above comes with it (the demo account keeps its `sub`), but accounts registered
on the old container, roles granted with `scripts/grant-role.sh` and the
master-realm `frontendUrl` the console tunnel needs do not. Grant the roles and
set the `frontendUrl` again (the commands are next to the keycloak service in
`docker-compose.prod.yml`), then run `scripts/wso2/setup.sh`, so WSO2 checks its
key-manager secret against the new realm. After that, recreating Keycloak
changes nothing.

## Bringing a realm that already exists up to date

None of the above reaches a Keycloak that imported an older version of this
file: once a realm exists in the database, editing this file changes nothing on
it. Patch it through the Admin API instead. The block below brings a realm
imported before the hardening up to date; run it from the repo checkout the
stack runs from, with the two secrets already in `.env`:

```bash
kc() { docker exec -i apextick-keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
id_of() { kc get clients -r apextick -q "clientId=$1" --fields id --format csv --noquotes; }
env_of() { sed -n "s/^$1=//p" .env | tail -n1; }

kc config credentials --server http://localhost:8080 --realm master \
  --user "$(env_of KEYCLOAK_ADMIN)" --password "$(env_of KEYCLOAK_ADMIN_PASSWORD)"

# The key-manager secret: the old one is public.
kc update "clients/$(id_of apextick-wso2-km)" -r apextick \
  -s "secret=$(env_of WSO2_KM_CLIENT_SECRET)"

# The password grant moves off the SPA's public client (and Keycloak's own
# admin-cli). Safe to re-run: apextick-loadtest is only created when missing.
[ -n "$(id_of apextick-loadtest)" ] || kc create clients -r apextick \
  -s clientId=apextick-loadtest -s publicClient=false \
  -s standardFlowEnabled=false -s directAccessGrantsEnabled=true \
  -s "secret=$(env_of LOADTEST_CLIENT_SECRET)" \
  -s 'defaultClientScopes=["web-origins","acr","profile","roles","basic","email","wso2-audience"]'
kc update "clients/$(id_of apextick-web)" -r apextick -s directAccessGrantsEnabled=false
kc update "clients/$(id_of admin-cli)" -r apextick -s directAccessGrantsEnabled=false

# Realm settings. Existing passwords, the demo account's included, keep working:
# the policy is checked when a password is set, not at login.
kc update realms/apextick -s sslRequired=external -s bruteForceProtected=true \
  -s permanentLockout=false -s failureFactor=10 -s waitIncrementSeconds=60 \
  -s maxFailureWaitSeconds=900 -s maxDeltaTimeSeconds=43200 \
  -s 'passwordPolicy=length(8) and maxLength(128) and notUsername and notEmail'

# Push the new key-manager secret to WSO2 and map apextick-loadtest there.
scripts/wso2/setup.sh
```

`setup.sh` checks `WSO2_KM_CLIENT_SECRET` against Keycloak before it touches
the gateway, and stops if Keycloak rejects it or still holds a publicly known
value, so running it before the rotation above can't break a working gateway.
To rotate either secret later, it is the same `kc update ... -s secret=...`
followed by `setup.sh` (the key manager) or nothing (`apextick-loadtest`, whose
secret only its callers need).
