#!/usr/bin/env bash
# Re-exports the booking service's OpenAPI document into the WSO2 API project.
#
# The gateway's contract is generated from the code, never hand-maintained:
# run this after changing a controller, commit the diff, and setup.sh will
# publish the new definition (it replaces an already-imported one too).
#
#   scripts/wso2/refresh-openapi.sh [booking-service-url]
#
# No running stack? The test suite exports the same document -- OpenApiExportTest
# boots the app against Testcontainers and writes it to target/openapi -- so
# the offline equivalent is:
#
#   (cd booking-service && ./mvnw test -Dtest=OpenApiExportTest -Dsurefire.failIfNoSpecifiedTests=false)
#   python3 scripts/wso2/normalise-openapi.py \
#       booking-service/target/openapi/api-docs.json wso2/apextick-api/openapi.json
#
# Either way, normalise-openapi.py strips the /api prefix and marks the
# operations booking-service serves anonymously as anonymous at the gateway.
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

python3 "$NORMALISE" "${OUT}.tmp" "$OUT"
rm -f "${OUT}.tmp"

echo "Wrote $OUT"
