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
| `APP_WEB_URL` | the deployed frontend's origin, allowed as an `apextick-web` redirect URI (prod: `https://<SERVER_IP>.nip.io`) | `http://localhost:3000` |
| `DEMO_USER_EMAIL` | the seeded `kalana` account's address | `kalana@apextick.local` |
| `SMTP_*` | outgoing mail | the bundled Mailpit |

A placeholder with no default that is **not** set is not an error: Keycloak
imports the literal text (the client secret becomes the string
`${WSO2_KM_CLIENT_SECRET}`, which anyone reading this repo knows). That is why
`docker-compose.prod.yml` must refuse to start without it, and why
`scripts/wso2/setup.sh` refuses to use a value that looks like a placeholder.

Generate a secret with `openssl rand -hex 32`.

## Hardening

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

### Rotating a secret on a realm that already exists

The import will not overwrite it, so set it through the Admin API and then let
`scripts/wso2/setup.sh` push the new value to WSO2's key-manager registration:

```bash
docker exec -it apextick-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user "$KEYCLOAK_ADMIN"
ID=$(docker exec apextick-keycloak /opt/keycloak/bin/kcadm.sh get clients -r apextick \
  -q clientId=apextick-wso2-km --fields id --format csv --noquotes)
docker exec apextick-keycloak /opt/keycloak/bin/kcadm.sh update "clients/$ID" -r apextick \
  -s "secret=$WSO2_KM_CLIENT_SECRET"
scripts/wso2/setup.sh
```
