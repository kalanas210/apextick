#!/usr/bin/env bash
# Seeds the k6 identity pool: loadtest-01 .. loadtest-NN in the apextick realm, all with
# LOADTEST_PASSWORD.
#
# booking-load-test.js needs a crowd of real accounts, not one login shared by every VU:
# seats.held_by is the token's `sub`, the hold rate limiter is per subject, and "no seat
# was held by two people" only means something when there are several people. The realm
# import seeds one demo account, and re-importing it would drop everyone who has
# self-registered since, so the pool is created through the Admin API instead -- the same
# approach as scripts/grant-admin.sh.
#
#   ./seed-loadtest-users.sh          # 20 users
#   ./seed-loadtest-users.sh 50       # 50 users
#
# Re-running is safe: existing accounts keep their id and just get the password reset.
#
# Environment:
#   LOADTEST_PASSWORD    required -- the password every pool account gets
#   LOADTEST_USER_PREFIX loadtest-              (must match the k6 script's)
#   KEYCLOAK_CONTAINER   apextick-keycloak
#   KEYCLOAK_URL         http://localhost:8080  (as seen from inside the container)
#   KEYCLOAK_REALM       apextick
#   KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD    default to the repo's .env
#
# These are throwaway load-test accounts on a local stack. Do not run this against an
# environment you care about.
set -euo pipefail

COUNT="${1:-20}"
PREFIX="${LOADTEST_USER_PREFIX:-loadtest-}"
CONTAINER="${KEYCLOAK_CONTAINER:-apextick-keycloak}"
URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${KEYCLOAK_REALM:-apextick}"

# Compose reads the same .env, so it is by definition what the running container was
# started with -- hardcoded defaults here would only be right for a stack nobody configured.
ENV_FILE="$(dirname "$0")/../.env"
if [ -f "$ENV_FILE" ]; then
  from_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n1 | tr -d '\r"'; }
  : "${KEYCLOAK_ADMIN:=$(from_env KEYCLOAK_ADMIN)}"
  : "${KEYCLOAK_ADMIN_PASSWORD:=$(from_env KEYCLOAK_ADMIN_PASSWORD)}"
  : "${LOADTEST_PASSWORD:=$(from_env LOADTEST_PASSWORD)}"
fi

if [ -z "${LOADTEST_PASSWORD:-}" ]; then
  echo "Set LOADTEST_PASSWORD -- the pool shares it with LOADTEST_USER in the k6 run." >&2
  exit 1
fi

ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
KCADM=/opt/keycloak/bin/kcadm.sh

kc() {
  docker exec -i "$CONTAINER" "$KCADM" "$@"
}

echo "==> authenticating as $ADMIN_USER against $URL"
kc config credentials --server "$URL" --realm master --user "$ADMIN_USER" --password "$ADMIN_PASSWORD" >/dev/null

created=0
reused=0
for i in $(seq 1 "$COUNT"); do
  username=$(printf '%s%02d' "$PREFIX" "$i")
  # create is not idempotent: a 409 means the account is already there, which is fine.
  if kc create users -r "$REALM" \
      -s "username=$username" \
      -s enabled=true \
      -s emailVerified=true \
      -s "email=$username@apextick.local" \
      -s "firstName=Load" -s "lastName=Test $i" >/dev/null 2>&1; then
    created=$((created + 1))
  else
    reused=$((reused + 1))
  fi
  kc set-password -r "$REALM" --username "$username" --new-password "$LOADTEST_PASSWORD" >/dev/null
done

echo "==> $created created, $reused already existed (${PREFIX}01..$(printf '%s%02d' "$PREFIX" "$COUNT"))"
echo
echo "Run the flash sale with:  k6 run -e LOADTEST_USER_COUNT=$COUNT booking-load-test.js"
