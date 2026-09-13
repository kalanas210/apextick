#!/usr/bin/env bash
# Grants the `admin` realm role. Kept so the commands already written down keep
# working; grant-role.sh does the work, for every role.
#
#   scripts/grant-admin.sh                 # grants to kalana
#   scripts/grant-admin.sh someone-else
set -euo pipefail

exec "$(dirname "$0")/grant-role.sh" admin "$@"
