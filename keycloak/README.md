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
| `SMTP_*` | outgoing mail | the bundled Mailpit |

A placeholder with no default that is **not** set is not an error: Keycloak
imports the literal text (the client secret becomes the string
`${WSO2_KM_CLIENT_SECRET}`, which anyone reading this repo knows). That is why
`docker-compose.prod.yml` must refuse to start without it, and why
`scripts/wso2/setup.sh` refuses to use a value that looks like a placeholder.

Generate a secret with `openssl rand -hex 32`.

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
