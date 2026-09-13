#!/usr/bin/env bash
# Grants an ApexTick realm role to a user on a Keycloak that has already imported
# the realm.
#
# The realm JSON in keycloak/import is only read when the realm does not yet exist
# in Keycloak's database. On a box that has been running -- production, or any local
# stack whose Keycloak has not been recreated since -- editing that file changes
# nothing, and recreating the container to force a re-import would drop every account
# that has self-registered since. So the running realm gets patched through the Admin
# API instead, to exactly the state the import file now describes.
#
#   scripts/grant-role.sh admin                # the admin panel, for kalana
#   scripts/grant-role.sh scanner steward-1    # a gate steward: the scanner and nothing else
#
# Roles:
#   admin     everything under /api/admin/**, and the gate
#   scanner   scans tickets at /api/gate/**, nothing else
#
# Environment (all optional):
#   KEYCLOAK_CONTAINER   apextick-keycloak
#   KEYCLOAK_URL         http://localhost:8080   (as seen from inside the container)
#   KEYCLOAK_REALM       apextick
#   KEYCLOAK_ADMIN       admin
#   KEYCLOAK_ADMIN_PASSWORD
set -euo pipefail

ROLE="${1:-}"
case "$ROLE" in
  admin) DESCRIPTION='ApexTick administrator: full access to /api/admin/**' ;;
  scanner) DESCRIPTION='ApexTick gate steward: scans tickets at /api/gate/** and nothing else' ;;
  *)
    echo "usage: $0 admin|scanner [username]" >&2
    exit 64
    ;;
esac

# Fall back to the repo's .env for Keycloak's bootstrap credentials. Compose reads
# the same file, so it is by definition what the running container was started
# with -- hardcoded defaults here would only be right for a stack nobody configured.
ENV_FILE="$(dirname "$0")/../.env"
if [ -f "$ENV_FILE" ]; then
  from_env() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -n1 | tr -d '\r"'; }
  : "${KEYCLOAK_ADMIN:=$(from_env KEYCLOAK_ADMIN)}"
  : "${KEYCLOAK_ADMIN_PASSWORD:=$(from_env KEYCLOAK_ADMIN_PASSWORD)}"
fi

USERNAME="${2:-kalana}"
CONTAINER="${KEYCLOAK_CONTAINER:-apextick-keycloak}"
URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${KEYCLOAK_REALM:-apextick}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

KCADM=/opt/keycloak/bin/kcadm.sh

kc() {
  docker exec -i "$CONTAINER" "$KCADM" "$@"
}

echo "==> authenticating as $ADMIN_USER against $URL"
kc config credentials --server "$URL" --realm master --user "$ADMIN_USER" --password "$ADMIN_PASSWORD" >/dev/null

if kc get "roles/$ROLE" -r "$REALM" >/dev/null 2>&1; then
  echo "==> realm role '$ROLE' already exists"
else
  echo "==> creating realm role '$ROLE'"
  kc create roles -r "$REALM" -s "name=$ROLE" -s "description=$DESCRIPTION" >/dev/null
fi

echo "==> granting '$ROLE' to $USERNAME"
# add-roles is idempotent: re-granting a role the user already holds is a no-op.
kc add-roles -r "$REALM" --uusername "$USERNAME" --rolename "$ROLE"

echo "==> $USERNAME now holds:"
kc get-roles -r "$REALM" --uusername "$USERNAME" --effective --fields name

echo
echo "Sign out and back in -- realm roles are baked into the access token at issue time."
