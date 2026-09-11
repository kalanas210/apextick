# shellcheck shell=bash
# Shared by setup.sh and smoke-test.sh -- sourced, not run.
#
#   env_default NAME...     fills each unset or empty NAME from the repo's .env.
#                           Compose reads the same file, so it is what the stack
#                           was actually started with (scripts/grant-admin.sh
#                           does the same).
#   resolve_public_origin   sets PUBLIC_ORIGIN to the origin Keycloak puts in the
#                           `iss` of every token: KC_HOSTNAME in production
#                           (docker-compose.prod.yml pins it to
#                           https://$SERVER_IP.nip.io), empty locally, where tokens
#                           carry whatever host the browser used (localhost:8180).
#                           Stops if .env's SERVER_IP disagrees with the Keycloak
#                           that is actually running.

APEXTICK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APEXTICK_ENV_FILE="$APEXTICK_ROOT/.env"

env_default() {
  local name value
  [ -f "$APEXTICK_ENV_FILE" ] || return 0
  for name in "$@"; do
    [ -n "${!name:-}" ] && continue
    value=$(sed -n "s/^$name=//p" "$APEXTICK_ENV_FILE" | tail -n1 | tr -d '\r"')
    printf -v "$name" '%s' "$value"
  done
}

resolve_public_origin() {
  local container="${KEYCLOAK_CONTAINER:-apextick-keycloak}" kc_env
  # .env.example's placeholder, not an address
  [ "${SERVER_IP:-}" = "YOUR_IP" ] && SERVER_IP=""

  if [ -z "${KC_HOSTNAME+set}" ]; then
    if kc_env=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$container" 2>/dev/null); then
      KC_HOSTNAME=$(printf '%s\n' "$kc_env" | sed -n 's/^KC_HOSTNAME=//p' | tr -d '\r')
    elif [ -n "${SERVER_IP:-}" ]; then
      # No Keycloak container to ask (not on the docker host?): assume the
      # production layout that SERVER_IP describes.
      KC_HOSTNAME="https://$SERVER_IP.nip.io"
    else
      KC_HOSTNAME=""
    fi
  fi
  PUBLIC_ORIGIN="${KC_HOSTNAME%/}"

  if [ -n "$PUBLIC_ORIGIN" ] && [ -n "${SERVER_IP:-}" ] && [ "$PUBLIC_ORIGIN" != "https://$SERVER_IP.nip.io" ]; then
    printf '\033[31mERROR: .env has SERVER_IP=%s, but Keycloak runs with KC_HOSTNAME=%s -- its tokens would never match. Fix .env or recreate the stack.\033[0m\n' \
      "$SERVER_IP" "$KC_HOSTNAME" >&2
    exit 1
  fi
}
