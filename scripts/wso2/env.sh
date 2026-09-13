# shellcheck shell=bash
# Shared by setup.sh and smoke-test.sh -- sourced, not run.
#
#   env_default NAME...     fills each unset or empty NAME from the repo's .env.
#                           Compose reads the same file, so it is what the stack
#                           was actually started with (scripts/grant-admin.sh
#                           does the same). A NAME .env lacks falls back to the
#                           running Keycloak container's environment.
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
  local name value kc_env="" kc_read=""
  for name in "$@"; do
    [ -n "${!name:-}" ] && continue
    value=""
    if [ -f "$APEXTICK_ENV_FILE" ]; then
      value=$(sed -n "s/^$name=//p" "$APEXTICK_ENV_FILE" | tail -n1 | tr -d '\r"')
    fi
    if [ -z "$value" ]; then
      # A .env written before the variable existed: take what the running
      # Keycloak was started with instead (compose's own fallback), which is
      # what its realm actually holds.
      if [ -z "$kc_read" ]; then
        kc_env=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' \
          "${KEYCLOAK_CONTAINER:-apextick-keycloak}" 2>/dev/null || true)
        kc_read=1
      fi
      value=$(printf '%s\n' "$kc_env" | sed -n "s/^$name=//p" | tail -n1 | tr -d '\r')
    fi
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
