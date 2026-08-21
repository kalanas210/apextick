#!/usr/bin/env bash
# Guard against the PolinRider / "Contagious Interview" config-file injection that hit this
# repo in June 2026 (obfuscated JS appended after ~280 spaces to postcss.config.mjs).
# Fails when: a known malware artifact is tracked, a *config.{js,mjs,cjs,ts} file is
# suspiciously large, or an injected-code marker appears in any tracked source file.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

MAX_CONFIG_BYTES="${MAX_CONFIG_BYTES:-4096}"
# obfuscator globals / decoder names / XOR seeds / hidden-padding signature
MARKERS='global\[.(!|_V|m|r|i).\]|global\.(i|r|m)=|rmcej%otb%|Cot%3t=shtP|_[$]_1e42|_0x[0-9a-f]{4,}=|eval\(atob\(|[ ]{120,}[^ ]'
fail=0

# 1. known artifact files
while IFS= read -r -d '' f; do
  echo "::error file=$f::malware artifact file is tracked ($f)"; fail=1
done < <(git ls-files -z | grep -z -E '(^|/)(temp_auto_push\.bat|temp_interactive_push\.bat|branch_structure\.json|config\.bat)$' || true)

# 2. oversized build/tool config files
while IFS= read -r -d '' f; do
  size=$(wc -c < "$f")
  if [ "$size" -gt "$MAX_CONFIG_BYTES" ]; then
    echo "::error file=$f::config file is ${size} bytes (> ${MAX_CONFIG_BYTES}); inspect for injected code"; fail=1
  fi
done < <(git ls-files -z | grep -z -E '(^|/)[A-Za-z0-9._-]*config\.(js|mjs|cjs|ts|mts|cts)$' | grep -z -v -E '(^|/)node_modules/' || true)

# 3. markers inside tracked text sources
while IFS= read -r -d '' f; do
  echo "::error file=$f::injected-code marker found in $f"; fail=1
done < <(git ls-files -z -- '*.js' '*.mjs' '*.cjs' '*.ts' '*.tsx' '*.jsx' '*.json' '*.html' '*.yml' '*.yaml' \
         | grep -z -v -E '(^|/)node_modules/|package-lock\.json$|\.lock$' \
         | xargs -0 -r grep -l -I -Z -E "$MARKERS" 2>/dev/null || true)

if [ "$fail" -ne 0 ]; then
  echo "Injected-code guard FAILED. See https://github.com/OpenSourceMalware/PolinRider" >&2
  exit 1
fi
echo "Injected-code guard OK (no artifacts, oversized configs or markers)."
