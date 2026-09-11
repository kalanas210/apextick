# Keycloak realm

`import/apextick-realm.json` is imported by `start-dev --import-realm` the first
time Keycloak starts on an empty database. After that the realm lives in the
database, and editing this file changes nothing on that instance -- patch a
running realm through the Admin API instead (see `scripts/grant-admin.sh`).

## Values that come from the environment

Keycloak substitutes `${VAR}` and `${VAR:default}` in the import file from the
Keycloak container's environment, so secrets and per-deployment URLs stay out
of git. The compose files pass these through from `.env`.

| Placeholder | Used for | Default in the realm |
| --- | --- | --- |
| `WSO2_KM_CLIENT_SECRET` | secret of `apextick-wso2-km`, the service account WSO2 uses to read and manage clients | none -- required |
| `LOADTEST_CLIENT_SECRET` | secret of `apextick-loadtest`, the only client that accepts the password grant | none -- required |
| `APP_WEB_URL` | the deployed frontend's origin, allowed as an `apextick-web` redirect URI (prod: `https://<SERVER_IP>.nip.io`) | `http://localhost:3000` |
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

The demo account's published password (`12345`, advertised on the sign-in
page) is shorter than that policy allows. Keycloak checks a plaintext seed
`value` against the policy too and would abort the whole import
(`invalidPasswordMinLengthMessage`), so it is seeded pre-hashed instead
(`secretData`/`credentialData`, the same shape a realm export produces), which
the policy has nothing to check against. Keycloak re-hashes it with the current
default algorithm at the first login. To regenerate it: PBKDF2WithHmacSHA512,
210000 iterations, a 16-byte random salt, a 512-bit key, both base64-encoded.

## Bringing a realm that already exists up to date

None of the above reaches a Keycloak that imported an older version of this
file -- production, for one, where `apextick-wso2-km` still has the secret
that used to be committed here. There are two ways to bring it up to date, and
the choice is really about the accounts in it: the dev-mode database lives
inside the container, so **any recreate drops every account registered since
the last import** and re-imports this file.

**Recreating is what a plain `docker compose -f docker-compose.prod.yml up -d`
does after this change**, because the keycloak service gained environment
variables and a loopback port. A fresh import gets everything above in one go
(the demo account keeps its `sub`); self-registered accounts are gone, and so
is every role granted with `scripts/grant-admin.sh` and the master-realm
`frontendUrl` the console tunnel needs (see docker-compose.prod.yml). Run
`scripts/wso2/setup.sh` afterwards so WSO2 picks up the new key-manager secret.

**To keep the accounts**, leave the running container alone -- update the
other services by name with `--no-deps` (e.g.
`docker compose -f docker-compose.prod.yml up -d --no-deps booking-service
notification-service frontend caddy wso2am`) -- and patch the realm through
the Admin API instead. The console's loopback port only appears when Keycloak
is eventually recreated; until then use `docker exec` as below. From the repo
checkout the stack runs from, with the two new secrets already in `.env`:

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
