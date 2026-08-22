#!/usr/bin/env bash
# Checks what the gateway actually does with each kind of caller.
#
# It reports rather than guesses: the Keycloak key-manager mapping has a known
# limitation (see docs/wso2.md), so the token pass-through case prints the
# gateway's own error code instead of quietly failing.
#
#   scripts/wso2/smoke-test.sh
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8280/api}"
KEYCLOAK="${KEYCLOAK:-http://localhost:8180}"
REALM="${REALM:-apextick}"
CLIENT_ID="${CLIENT_ID:-apextick-web}"
USERNAME="${LOADTEST_USER:-kalana}"
PASSWORD="${LOADTEST_PASSWORD:-12345}"
EVENT_SLUG="${EVENT_SLUG:-india-australia-semi-final}"

pass=0; fail=0
ok()   { printf '  \033[32mPASS\033[0m %-44s %s\n' "$1" "$2"; pass=$((pass+1)); }
bad()  { printf '  \033[31mFAIL\033[0m %-44s %s\n' "$1" "$2"; fail=$((fail+1)); }
note() { printf '  \033[33mNOTE\033[0m %-44s %s\n' "$1" "$2"; }

# status + WSO2 error code for one request
probe() {
  local url="$1"; shift
  local code body
  code=$(curl -s -o /dev/null -w '%{http_code}' "$@" "$url")
  body=$(curl -s "$@" "$url" | python -c "import json,sys
try: print(json.load(sys.stdin).get('code',''))
except Exception: print('')" 2>/dev/null)
  printf '%s %s' "$code" "$body"
}

echo "Gateway: $GATEWAY"

TOKEN=$(curl -s "$KEYCLOAK/realms/$REALM/protocol/openid-connect/token" \
  -d grant_type=password -d "client_id=$CLIENT_ID" \
  -d "username=$USERNAME" -d "password=$PASSWORD" \
  | python -c "import json,sys;print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null)
[ -n "$TOKEN" ] || { echo "ERROR: could not get a Keycloak token" >&2; exit 1; }

URL="$GATEWAY/events/$EVENT_SLUG"

echo
echo "1. Unauthenticated traffic dies at the edge"
read -r code wsocode <<<"$(probe "$URL")"
# 900902 = Missing Credentials. The backend is never dialled.
if [ "$code" = "401" ] && [ "$wsocode" = "900902" ]; then
  ok "no token is rejected" "401 / $wsocode"
else
  bad "no token is rejected" "expected 401 / 900902, got $code / ${wsocode:-none}"
fi

read -r code wsocode <<<"$(probe "$URL" -H "Authorization: Bearer not-a-real-token")"
if [ "$code" != "200" ]; then
  ok "a garbage token is rejected" "$code / ${wsocode:-none}"
else
  bad "a garbage token is rejected" "it got through"
fi

echo
echo "2. A real Keycloak token is recognised"
read -r code wsocode <<<"$(probe "$URL" -H "Authorization: Bearer $TOKEN")"
case "$code:$wsocode" in
  200:*)
    ok "Keycloak token reaches the backend" "200"
    SUBSCRIBED=1
    ;;
  403:900908)
    # The signature and issuer checked out — the gateway got far enough to look
    # for a subscription, which is the step that needs the key mapping.
    note "Keycloak token validated, not subscribed" "403 / 900908"
    note "" "see the 'Known limitations' section of docs/wso2.md"
    SUBSCRIBED=0
    ;;
  *)
    bad "Keycloak token is validated" "expected 200 or 403/900908, got $code / ${wsocode:-none}"
    SUBSCRIBED=0
    ;;
esac

echo
echo "3. A burst of seat holds is throttled"
if [ "${SUBSCRIBED:-0}" != "1" ]; then
  note "skipped" "throttling only kicks in after subscription validation"
else
  SEAT=$(curl -s -H "Authorization: Bearer $TOKEN" "$GATEWAY/events/$EVENT_SLUG/seats" \
    | python -c "import json,sys;s=json.load(sys.stdin);print(next((x['id'] for x in s if x['status']=='AVAILABLE'),''))" 2>/dev/null)
  if [ -z "$SEAT" ]; then
    note "skipped" "no AVAILABLE seat to hammer — reset the demo data"
  else
    throttled=0
    for _ in $(seq 1 40); do
      code=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
        -H "Authorization: Bearer $TOKEN" "$GATEWAY/seats/$SEAT/hold")
      [ "$code" = "429" ] && throttled=$((throttled+1))
    done
    if [ "$throttled" -gt 0 ]; then
      ok "burst of 40 holds" "$throttled rejected with 429"
    else
      bad "burst of 40 holds" "nothing was throttled"
    fi
  fi
fi

printf '\n%s passed, %s failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
