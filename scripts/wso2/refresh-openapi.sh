#!/usr/bin/env bash
# Re-exports the booking service's OpenAPI document into the WSO2 API project.
#
# The gateway's contract is generated from the code, never hand-maintained:
# run this after changing a controller, commit the diff, and setup.sh will
# publish the new definition.
#
#   scripts/wso2/refresh-openapi.sh [booking-service-url]
set -euo pipefail

BOOKING_URL="${1:-${BOOKING_URL:-http://localhost:8081}}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/wso2/apextick-api/openapi.json"
NORMALISE="$(dirname "$0")/normalise-openapi.py"

mkdir -p "$(dirname "$OUT")"

echo "Fetching ${BOOKING_URL}/v3/api-docs ..."
if ! curl -fsS "${BOOKING_URL}/v3/api-docs" -o "${OUT}.tmp"; then
  echo "ERROR: could not reach ${BOOKING_URL}/v3/api-docs — is booking-service running?" >&2
  rm -f "${OUT}.tmp"
  exit 1
fi

python "$NORMALISE" "${OUT}.tmp" "$OUT"
rm -f "${OUT}.tmp"

echo "Wrote $OUT"
