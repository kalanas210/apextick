#!/usr/bin/env bash
# Configures a fresh WSO2 API Manager to front the booking service.
#
# Everything here goes through the documented Publisher / Admin / DevPortal REST
# APIs, so the gateway's configuration lives in version control rather than in
# somebody's browser session. Re-running is safe: each step checks for what it
# is about to create.
#
#   docker compose --profile wso2 up -d
#   scripts/wso2/setup.sh
#
# Run it on the docker host: it reads the repo's .env and asks the running
# Keycloak container which issuer its tokens carry.
#
# Environment (all optional):
#   WSO2_HOST            https://localhost:9443
#   WSO2_USER            admin
#   WSO2_PASSWORD        WSO2_ADMIN_PASSWORD (environment, then .env -- the value
#                        deployment.toml gives the super admin), else admin
#   WSO2_BACKEND         http://booking-service:8081/api  (as seen from the gateway --
#                        every controller in booking-service is mapped under /api, and
#                        the operation paths below have that prefix stripped so the
#                        gateway's own /api context doesn't double up client-side; the
#                        backend URL puts the prefix back for the upstream call)
#   WSO2_KC_WELLKNOWN    Keycloak discovery URL, gateway-internal
#   WSO2_KC_ISSUER       the `iss` claim in tokens the browser gets. Defaults to
#                        Keycloak's KC_HOSTNAME + /realms/apextick when it has one
#                        (production: https://$SERVER_IP.nip.io), otherwise
#                        http://localhost:8180/realms/apextick. Setting it to a host
#                        other than KC_HOSTNAME's is refused -- no token would match.
#                        Locally it must also be a host this gateway container can
#                        resolve and reach itself (the Keycloak connector dials it
#                        directly, not just the JWKS URL below). localhost can't mean
#                        that from inside a container, so this only works for a
#                        browser hitting the app via http://localhost:3000 when
#                        testing outside the gateway; to exercise the gateway path
#                        locally use host.docker.internal instead -- see docs/wso2.md.
#   KEYCLOAK_CONTAINER   apextick-keycloak (where KC_HOSTNAME is read from)
#   KEYCLOAK             Keycloak as this host reaches it, to check the secret below
#                        before WSO2 is told about it: KC_HOSTNAME when it has one,
#                        otherwise http://localhost:8180
#
# Required (from the environment, or else the repo's .env):
#   WSO2_KM_CLIENT_SECRET  the secret Keycloak imported for apextick-wso2-km. The realm
#                          file only carries a ${WSO2_KM_CLIENT_SECRET} placeholder, so
#                          there is no default here to fall back on.
set -euo pipefail

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
info() { printf '    %s\n' "$1"; }
die()  { printf '\033[31mERROR: %s\033[0m\n' "$1" >&2; exit 1; }

. "$(dirname "$0")/env.sh"
ROOT="$APEXTICK_ROOT"

env_default WSO2_KM_CLIENT_SECRET WSO2_ADMIN_PASSWORD SERVER_IP
resolve_public_origin

if [ -n "$PUBLIC_ORIGIN" ]; then
  DEFAULT_ISSUER="$PUBLIC_ORIGIN/realms/apextick"
  GATEWAY_URL="$PUBLIC_ORIGIN/api"   # through Caddy, which routes /api to the gateway
else
  DEFAULT_ISSUER="http://localhost:8180/realms/apextick"
  GATEWAY_URL="http://localhost:8280/api"
fi

WSO2_HOST="${WSO2_HOST:-https://localhost:9443}"
WSO2_USER="${WSO2_USER:-admin}"
WSO2_PASSWORD="${WSO2_PASSWORD:-${WSO2_ADMIN_PASSWORD:-admin}}"
WSO2_BACKEND="${WSO2_BACKEND:-http://booking-service:8081/api}"
WSO2_KC_WELLKNOWN="${WSO2_KC_WELLKNOWN:-http://keycloak:8080/realms/apextick/.well-known/openid-configuration}"
WSO2_KC_ISSUER="${WSO2_KC_ISSUER:-$DEFAULT_ISSUER}"
WSO2_KC_JWKS="${WSO2_KC_JWKS:-http://keycloak:8080/realms/apextick/protocol/openid-connect/certs}"
WSO2_KC_BASE="${WSO2_KC_BASE:-http://keycloak:8080/realms/apextick}"
WSO2_KM_CLIENT_ID="${WSO2_KM_CLIENT_ID:-apextick-wso2-km}"
WSO2_KM_CLIENT_SECRET="${WSO2_KM_CLIENT_SECRET:-}"
WSO2_SPA_CLIENT_ID="${WSO2_SPA_CLIENT_ID:-apextick-web}"
WSO2_LOADTEST_CLIENT_ID="${WSO2_LOADTEST_CLIENT_ID:-apextick-loadtest}"

[ -n "$WSO2_KM_CLIENT_SECRET" ] \
  || die "WSO2_KM_CLIENT_SECRET is not set -- add the value Keycloak was started with to .env (see docs/wso2.md)"
case "$WSO2_KM_CLIENT_SECRET" in
  # What Keycloak stores when the variable never reached it: a publicly known
  # secret, and one the gateway must not be configured to rely on.
  '${'*) die "WSO2_KM_CLIENT_SECRET is the unresolved placeholder '$WSO2_KM_CLIENT_SECRET'" ;;
  # The value the realm file used to commit, and so what a realm imported before
  # that changed still holds. Public either way: rotate it first.
  apextick-wso2-km-secret)
    die "WSO2_KM_CLIENT_SECRET is the old committed secret -- rotate it (keycloak/README.md) and put the new one in .env" ;;
esac

# A key manager registered with the wrong issuer rejects every token, and the
# only symptom is a 401 on every authenticated call -- so refuse up front.
if [ -n "$PUBLIC_ORIGIN" ]; then
  case "$WSO2_KC_ISSUER" in
    "$PUBLIC_ORIGIN/realms/"*) ;;
    *) die "WSO2_KC_ISSUER=$WSO2_KC_ISSUER, but Keycloak issues tokens as $PUBLIC_ORIGIN (KC_HOSTNAME)" ;;
  esac
fi

KEYCLOAK="${KEYCLOAK:-${PUBLIC_ORIGIN:-http://localhost:8180}}"

# The key manager below is re-applied even on a gateway that already works, so
# a secret Keycloak doesn't hold -- a freshly generated one in .env, say, on a
# realm that was imported before the secret moved out of the realm file -- would
# break a working gateway. Ask Keycloak first.
step "Checking WSO2_KM_CLIENT_SECRET against Keycloak"
km_token_code() {
  curl -s -o /dev/null -w '%{http_code}' -d grant_type=client_credentials \
    --data-urlencode "client_id=$WSO2_KM_CLIENT_ID" --data-urlencode "client_secret=$1" \
    "$KEYCLOAK/realms/apextick/protocol/openid-connect/token" || true
}
CODE=$(km_token_code "$WSO2_KM_CLIENT_SECRET")
case "$CODE" in
  200) info "accepted by $KEYCLOAK" ;;
  400|401)
    for known in '${WSO2_KM_CLIENT_SECRET}' apextick-wso2-km-secret; do
      if [ "$(km_token_code "$known")" = "200" ]; then
        die "Keycloak's secret for $WSO2_KM_CLIENT_ID is '$known', which anyone reading this repo knows -- rotate it (keycloak/README.md), then re-run"
      fi
    done
    die "Keycloak rejects WSO2_KM_CLIENT_SECRET for $WSO2_KM_CLIENT_ID ($CODE) -- .env and the realm disagree; see keycloak/README.md" ;;
  *) printf '\033[33m    WARNING: could not check it -- %s answered %s\033[0m\n' "$KEYCLOAK" "${CODE:-nothing}" ;;
esac

API_NAME="ApexTickAPI"
API_CONTEXT="api"   # no leading slash: Git Bash would rewrite it as a path
API_VERSION="1.0.0"
POLICY_NAME="ApexTickHoldBurst"
APP_NAME="ApexTickWeb"
LOADTEST_APP_NAME="ApexTickLoadTest"
OPENAPI="$ROOT/wso2/apextick-api/openapi.json"

# WSO2 serves the portals over a self-signed certificate.
CURL=(curl -sk)

# Scratch space for the API document we round-trip through the Publisher API.
API_TMP="$(mktemp -t apextick-api.XXXXXX)"
trap 'rm -f "$API_TMP"' EXIT

[ -f "$OPENAPI" ] || die "missing $OPENAPI — run scripts/wso2/refresh-openapi.sh first"

# --------------------------------------------------------------------------
step "Waiting for WSO2 to accept requests"
for i in $(seq 1 60); do
  if "${CURL[@]}" -o /dev/null -w '' "$WSO2_HOST/services/Version" 2>/dev/null; then
    info "up after ~$((i * 10))s"
    break
  fi
  [ "$i" = 60 ] && die "WSO2 did not come up within 10 minutes"
  sleep 10
done

# --------------------------------------------------------------------------
step "Registering a REST client (dynamic client registration)"
DCR=$("${CURL[@]}" -u "$WSO2_USER:$WSO2_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{"callbackUrl":"http://localhost","clientName":"apextick_setup","owner":"'"$WSO2_USER"'","grantType":"password refresh_token","saasApp":true}' \
  "$WSO2_HOST/client-registration/v0.17/register")

CLIENT_ID=$(printf '%s' "$DCR" | python3 -c "import json,sys;print(json.load(sys.stdin).get('clientId',''))" 2>/dev/null || true)
CLIENT_SECRET=$(printf '%s' "$DCR" | python3 -c "import json,sys;print(json.load(sys.stdin).get('clientSecret',''))" 2>/dev/null || true)
[ -n "$CLIENT_ID" ] || die "client registration failed: $DCR"
info "client id ${CLIENT_ID:0:8}…"

SCOPES="apim:api_view apim:api_create apim:api_publish apim:api_manage apim:subscribe apim:app_manage apim:sub_manage apim:admin apim:tier_view apim:tier_manage apim:keymanagers_manage"
TOKEN=$("${CURL[@]}" -u "$CLIENT_ID:$CLIENT_SECRET" \
  -d "grant_type=password&username=$WSO2_USER&password=$WSO2_PASSWORD&scope=$(printf '%s' "$SCOPES" | sed 's/ /%20/g')" \
  "$WSO2_HOST/oauth2/token" \
  | python3 -c "import json,sys;print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || true)
[ -n "$TOKEN" ] || die "could not obtain an admin token"
AUTH=(-H "Authorization: Bearer $TOKEN")
info "token acquired"

api() { "${CURL[@]}" "${AUTH[@]}" "$@"; }

# --------------------------------------------------------------------------
step "Registering Keycloak as a key manager"
# Lets the gateway accept the very same access tokens the SPA already holds,
# instead of issuing a second set of credentials nobody else understands.
info "issuer $WSO2_KC_ISSUER"
KM_EXISTS=$(api "$WSO2_HOST/api/am/admin/v4/key-managers" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(next((k['id'] for k in d.get('list',[]) if k.get('name')=='Keycloak'),''))" 2>/dev/null || true)

KM_BODY=$(python3 - "$WSO2_KC_WELLKNOWN" "$WSO2_KC_ISSUER" "$WSO2_KC_JWKS" \
                   "$WSO2_KC_BASE" "$WSO2_KM_CLIENT_ID" "$WSO2_KM_CLIENT_SECRET" <<'PY'
import json, sys

wellknown, issuer, jwks, base, client_id, client_secret = sys.argv[1:7]
oidc = f"{base}/protocol/openid-connect"
print(json.dumps({
    "name": "Keycloak",
    "displayName": "Keycloak (apextick realm)",
    # One of the connector types this distribution ships; see
    # GET /api/am/admin/v4/settings -> keyManagerConfiguration for the list.
    "type": "KeyCloak",
    "description": "Validates the access tokens the ApexTick SPA already holds.",
    "wellKnownEndpoint": wellknown,
    # The issuer must be the `iss` the browser's token actually carries, which is
    # the public Keycloak URL — not the internal one the gateway dials.
    "issuer": issuer,
    "clientRegistrationEndpoint": f"{base}/clients-registrations/default",
    "introspectionEndpoint": f"{oidc}/token/introspect",
    "tokenEndpoint": f"{oidc}/token",
    "revokeEndpoint": f"{oidc}/revoke",
    "userInfoEndpoint": f"{oidc}/userinfo",
    "authorizeEndpoint": f"{oidc}/auth",
    "certificates": {"type": "JWKS", "value": jwks},
    # Self-validate: the gateway checks the signature against the realm's JWKS
    # rather than calling introspection on every single request.
    "enableSelfValidationJWT": True,
    "enableTokenGeneration": True,
    "enableTokenEncryption": False,
    "enableTokenHashing": False,
    "enableMapOAuthConsumerApps": True,
    "enableOAuthAppCreation": True,
    "claimMapping": [],
    # Keycloak puts the client id in `azp`, so that is what identifies the
    # subscribing application to the throttling engine.
    "consumerKeyClaim": "azp",
    "scopesClaim": "scope",
    "enabled": True,
    "availableGrantTypes": ["authorization_code", "password", "refresh_token", "client_credentials"],
    "additionalProperties": {"client_id": client_id, "client_secret": client_secret},
}))
PY
)

if [ -n "$KM_EXISTS" ]; then
  # Updated in place rather than skipped, so a rotated WSO2_KM_CLIENT_SECRET (or a
  # corrected issuer) actually reaches a gateway that was configured earlier.
  KM_RESULT=$(api -X PUT -H 'Content-Type: application/json' -d "$KM_BODY" \
    -w '\n%{http_code}' "$WSO2_HOST/api/am/admin/v4/key-managers/$KM_EXISTS")
  KM_CODE=$(printf '%s' "$KM_RESULT" | tail -1)
  if [ "$KM_CODE" = "200" ]; then
    info "already registered ($KM_EXISTS), configuration re-applied"
  else
    printf '\033[33m    WARNING: updating key manager %s returned %s -- it keeps its previous settings\033[0m\n' "$KM_EXISTS" "$KM_CODE"
    printf '    %s\n' "$(printf '%s' "$KM_RESULT" | head -c 400)"
  fi
else
  KM_RESULT=$(api -H 'Content-Type: application/json' -d "$KM_BODY" \
    -w '\n%{http_code}' "$WSO2_HOST/api/am/admin/v4/key-managers")
  KM_CODE=$(printf '%s' "$KM_RESULT" | tail -1)
  if [ "$KM_CODE" = "201" ]; then
    info "registered"
  else
    # Not fatal: the API below still works with the Resident Key Manager, which
    # is enough to demonstrate gateway auth and throttling. See docs/wso2.md.
    printf '\033[33m    WARNING: key manager registration returned %s — falling back to the Resident Key Manager\033[0m\n' "$KM_CODE"
    printf '    %s\n' "$(printf '%s' "$KM_RESULT" | head -c 400)"
  fi
fi

# --------------------------------------------------------------------------
step "Creating the burst-throttling policy"
# The point of putting a gateway in front of a flash sale: shed the burst at the
# edge so the booking service never sees it.
POLICY_EXISTS=$(api "$WSO2_HOST/api/am/admin/v4/throttling/policies/advanced" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(next((p['policyId'] for p in d.get('list',[]) if p.get('policyName')=='$POLICY_NAME'),''))" 2>/dev/null || true)

if [ -n "$POLICY_EXISTS" ]; then
  info "already exists ($POLICY_EXISTS)"
else
  POLICY_BODY='{"policyName":"'"$POLICY_NAME"'","displayName":"'"$POLICY_NAME"'","description":"10 seat-hold requests per second, per subscriber","defaultLimit":{"type":"REQUESTCOUNTLIMIT","requestCount":{"timeUnit":"s","unitTime":1,"requestCount":10}}}'
  CODE=$(api -H 'Content-Type: application/json' -d "$POLICY_BODY" \
    -o /dev/null -w '%{http_code}' "$WSO2_HOST/api/am/admin/v4/throttling/policies/advanced")
  [ "$CODE" = "201" ] && info "created" || info "create returned $CODE"
fi

# --------------------------------------------------------------------------
step "Importing the API definition"
API_ID=$(api "$WSO2_HOST/api/am/publisher/v4/apis?query=name:$API_NAME" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(next((a['id'] for a in d.get('list',[]) if a.get('name')=='$API_NAME'),''))" 2>/dev/null || true)

ADDITIONAL=$(python3 - "$API_NAME" "$API_CONTEXT" "$API_VERSION" "$WSO2_BACKEND" <<'PY'
import json, sys
name, context, version, backend = sys.argv[1:5]
context = "/" + context.lstrip("/")
print(json.dumps({
    "name": name,
    "context": context,
    "version": version,
    "isDefaultVersion": True,
    "type": "HTTP",
    "transport": ["http", "https"],
    "policies": ["Unlimited"],
    "visibility": "PUBLIC",
    "endpointConfig": {
        "endpoint_type": "http",
        "sandbox_endpoints": {"url": backend},
        "production_endpoints": {"url": backend},
    },
}))
PY
)

if [ -n "$API_ID" ]; then
  # Re-applied rather than skipped, so a regenerated contract -- new operations,
  # ones that became anonymous -- reaches an API an earlier run imported. The
  # revision deployed below is what puts it on the gateway.
  CODE=$(api -X PUT -F "file=@$OPENAPI" -o /dev/null -w '%{http_code}' \
    "$WSO2_HOST/api/am/publisher/v4/apis/$API_ID/swagger")
  [ "$CODE" = "200" ] || die "updating the definition of $API_ID returned $CODE"
  info "already imported ($API_ID), definition updated"
else
  IMPORT=$(api -H 'Content-Type: multipart/form-data' \
    -F "file=@$OPENAPI" -F "additionalProperties=$ADDITIONAL" \
    "$WSO2_HOST/api/am/publisher/v4/apis/import-openapi")
  API_ID=$(printf '%s' "$IMPORT" | python3 -c "import json,sys;print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)
  [ -n "$API_ID" ] || die "import failed: $(printf '%s' "$IMPORT" | head -c 500)"
  info "imported as $API_ID"
fi

# --------------------------------------------------------------------------
step "Applying the burst policy to the seat-hold operations"
api "$WSO2_HOST/api/am/publisher/v4/apis/$API_ID" > "$API_TMP"
python3 - "$API_TMP" "$POLICY_NAME" <<'PY'
import json, sys
path, policy = sys.argv[1], sys.argv[2]
api = json.load(open(path))
touched = 0
for op in api.get("operations", []):
    # every write path that a flash sale hammers
    if "/hold" in op.get("target", "") or op.get("target", "").endswith("/holds"):
        if op.get("verb") in ("POST", "PUT"):
            op["throttlingPolicy"] = policy
            touched += 1
json.dump(api, open(path, "w"))
print(f"    tagged {touched} operation(s)")
PY
CODE=$(api -H 'Content-Type: application/json' --data-binary "@$API_TMP" \
  -o /dev/null -w '%{http_code}' -X PUT "$WSO2_HOST/api/am/publisher/v4/apis/$API_ID")
info "update returned $CODE"

# --------------------------------------------------------------------------
step "Deploying a revision to the gateway"
# Only a revision ever reaches the gateway: the definition and policies above
# change the Publisher's working copy, and none of it is served until a
# snapshot of it is deployed. So from here on a failure is fatal -- reporting
# success would leave the gateway on the previous contract with nobody told.
#
# WSO2 caps the revisions one API may hold (five by default) and never discards
# any itself, while every run of this script adds one -- and in production the
# database lives on a volume, so they outlast the container. At the cap a create
# is refused with code 900351; on exactly that refusal the oldest undeployed
# revision is deleted and the create retried. A deployed revision is never
# deleted (WSO2 refuses to anyway): the one the gateway serves stays until the
# new one replaces it, and the newer undeployed ones remain as rollback points.
REVISIONS_URL="$WSO2_HOST/api/am/publisher/v4/apis/$API_ID/revisions"

# Sets REV to the new revision's id, or leaves it empty and sets REV_CODE and
# REV_BODY to what WSO2 answered instead.
create_revision() {
  local result
  result=$(api -H 'Content-Type: application/json' \
    -d '{"description":"configured by scripts/wso2/setup.sh"}' \
    -w '\n%{http_code}' "$REVISIONS_URL" || true)
  REV_CODE=$(printf '%s' "$result" | tail -1)
  REV_BODY=$(printf '%s' "$result" | sed '$d')
  REV=""
  if [ "$REV_CODE" = "201" ]; then
    REV=$(printf '%s' "$REV_BODY" | python3 -c "import json,sys;print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)
  fi
}

at_revision_limit() {
  [ "$(printf '%s' "$REV_BODY" | python3 -c "import json,sys;print(json.load(sys.stdin).get('code',''))" 2>/dev/null || true)" = "900351" ]
}

create_revision
while [ -z "$REV" ] && at_revision_limit; do
  # deployed:false filters server-side; deploymentInfo is checked again so a
  # query WSO2 ignored could still never hand back a deployed revision.
  OLDEST=$(api "$REVISIONS_URL?query=deployed:false" | python3 -c "import json,sys
revs = [r for r in json.load(sys.stdin).get('list', []) if not r.get('deploymentInfo')]
print(min(revs, key=lambda r: int(r.get('createdTime') or 0))['id'] if revs else '')") \
    || die "could not list the undeployed revisions of $API_ID"
  [ -n "$OLDEST" ] \
    || die "$API_ID is at WSO2's revision limit and every revision is deployed -- undeploy one in the Publisher, then re-run"
  CODE=$(api -X DELETE -o /dev/null -w '%{http_code}' "$REVISIONS_URL/$OLDEST" || true)
  [ "$CODE" = "200" ] || die "deleting stale revision $OLDEST of $API_ID returned $CODE"
  info "at the revision limit -- deleted the oldest undeployed revision ($OLDEST)"
  create_revision
done
[ -n "$REV" ] \
  || die "creating a revision of $API_ID returned $REV_CODE, so the gateway still serves the previous one: $(printf '%s' "$REV_BODY" | head -c 400)"
info "revision $REV created"

DEPLOY=$(api -H 'Content-Type: application/json' \
  -d '[{"name":"Default","vhost":"localhost","displayOnDevportal":true}]' \
  -w '\n%{http_code}' "$WSO2_HOST/api/am/publisher/v4/apis/$API_ID/deploy-revision?revisionId=$REV" || true)
CODE=$(printf '%s' "$DEPLOY" | tail -1)
[ "$CODE" = "201" ] \
  || die "deploying revision $REV returned $CODE, so the gateway still serves the previous one: $(printf '%s' "$DEPLOY" | sed '$d' | head -c 400)"
info "revision $REV deployed to the Default gateway"

# --------------------------------------------------------------------------
step "Publishing"
CODE=$(api -o /dev/null -w '%{http_code}' -X POST \
  "$WSO2_HOST/api/am/publisher/v4/apis/change-lifecycle?apiId=$API_ID&action=Publish")
info "lifecycle change returned $CODE"

# --------------------------------------------------------------------------
# The gateway only accepts a token whose consumer key (the `azp` claim) belongs
# to an application subscribed to the API. The Keycloak clients here are ones
# people already log in with directly, not ones WSO2 minted itself, so each is
# attached with a BYOK mapping (map-keys) rather than the usual "generate keys"
# flow -- one application per client, since an application holds one
# production key per key manager.
#
# Mapped by client id alone, never with the secret: the gateway validates
# tokens, it doesn't need a client's credentials, and WSO2's Keycloak connector
# rejects any non-blank consumerSecret as "wrong for the given consumer key"
# (its client lookup doesn't return Keycloak's secret to compare against).
#   subscribe_client <application> <description> <keycloak client id>
subscribe_client() {
  local app="$1" description="$2" client_id="$3"
  local app_id code body

  step "Subscribing $app and mapping $client_id onto it"
  app_id=$(api "$WSO2_HOST/api/am/devportal/v3/applications" \
    | python3 -c "import json,sys;d=json.load(sys.stdin);print(next((a['applicationId'] for a in d.get('list',[]) if a.get('name')==sys.argv[1]),''))" "$app" 2>/dev/null || true)

  if [ -z "$app_id" ]; then
    body=$(python3 -c "import json,sys;print(json.dumps({'name':sys.argv[1],'throttlingPolicy':'Unlimited','description':sys.argv[2]}))" "$app" "$description")
    app_id=$(api -H 'Content-Type: application/json' -d "$body" \
      "$WSO2_HOST/api/am/devportal/v3/applications" \
      | python3 -c "import json,sys;print(json.load(sys.stdin).get('applicationId',''))" 2>/dev/null || true)
  fi
  if [ -z "$app_id" ]; then
    info "could not create the application -- skipped"
    return 0
  fi
  info "application $app_id"

  code=$(api -H 'Content-Type: application/json' \
    -d '{"applicationId":"'"$app_id"'","apiId":"'"$API_ID"'","throttlingPolicy":"Unlimited"}' \
    -o /dev/null -w '%{http_code}' "$WSO2_HOST/api/am/devportal/v3/subscriptions")
  case "$code" in
    201) info "subscribed" ;;
    409) info "already subscribed" ;;
    *)   info "subscription returned $code" ;;
  esac

  body=$(python3 -c "import json,sys;print(json.dumps({'consumerKey':sys.argv[1],'consumerSecret':'','keyManager':'Keycloak','keyType':'PRODUCTION'}))" "$client_id")
  code=$(api -H 'Content-Type: application/json' -d "$body" \
    -o /dev/null -w '%{http_code}' "$WSO2_HOST/api/am/devportal/v3/applications/$app_id/map-keys")
  case "$code" in
    200) info "mapped" ;;
    409) info "already mapped" ;;
    *)   info "map-keys returned $code -- see docs/wso2.md if this is 401/invalid_token" ;;
  esac
}

subscribe_client "$APP_NAME" "ApexTick web client" "$WSO2_SPA_CLIENT_ID"

# k6 and smoke-test.sh log in through apextick-loadtest, so its tokens carry
# azp=apextick-loadtest and need a subscription of their own.
subscribe_client "$LOADTEST_APP_NAME" "k6 and smoke-test.sh" "$WSO2_LOADTEST_CLIENT_ID"

printf '\n\033[32mDone.\033[0m Gateway: %s  ·  Publisher: %s/publisher\n' "$GATEWAY_URL" "$WSO2_HOST"
printf 'Verify with: scripts/wso2/smoke-test.sh\n'
