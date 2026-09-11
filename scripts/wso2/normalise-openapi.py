"""Prepares the booking service's OpenAPI document for import into WSO2.

The gateway publishes the API at context ``/api``, so the paths in the
definition have to be relative to that — otherwise every route would be served
at ``/api/api/...``. Springdoc emits them with the prefix included, so strip it
here rather than expecting anyone to remember.

It also marks the operations booking-service serves to anonymous callers as
anonymous at the gateway too (see ANONYMOUS below); without that WSO2 imports
every resource as token-required.

Output is pretty-printed and key-sorted so the committed file diffs readably.

    python normalise-openapi.py <source.json> <dest.json>
"""

import json
import sys

PREFIX = "/api"

# Operations the gateway must pass through without a token. This is the
# permitAll() list from booking-service's SecurityConfig, restricted to what is
# in the document (actuator, swagger and /api/ws are not) and written relative
# to the /api context. Keep the two in step: the SPA reads the catalogue and
# payment config with no bearer at all, and Stripe signs its webhook instead of
# sending one, so a rule missing here is a 401 at the gateway for a request the
# service would have served. "*" matches one path segment, a trailing "**" any
# number of them (including none, like Spring's "/api/events/**").
ANONYMOUS = [
    ("GET", "/series/**"),                 # GET  /api/series/**
    ("GET", "/events/**"),                 # GET  /api/events/**
    ("GET", "/payments/config"),           # GET  /api/payments/config
    ("POST", "/payments/stripe/webhook"),  # POST /api/payments/stripe/webhook
]

# ...minus the operations those globs happen to cover that still answer for
# the caller. SecurityConfig lets them through anonymously, but without a user
# they have nothing to return, so the gateway keeps asking for a token.
AUTHENTICATED = [
    ("GET", "/events/*/holds/me"),
]

# WSO2's per-resource security switch, read by its OpenAPI importer
# (APIConstants.SWAGGER_X_AUTH_TYPE). "None" turns authentication off for that
# resource; anything else, or no key at all, means a token is required. WSO2
# ignores the standard `security: []` override, which is set as well so other
# OpenAPI tooling reads the document the same way.
AUTH_TYPE_KEY = "x-auth-type"
AUTH_TYPE_NONE = "None"


def strip_prefix(path: str) -> str:
    if path == PREFIX:
        return "/"
    if path.startswith(PREFIX + "/"):
        return path[len(PREFIX):]
    return path


def matches(pattern: str, path: str) -> bool:
    want = pattern.strip("/").split("/")
    have = path.strip("/").split("/")
    if want[-1] == "**":
        want = want[:-1]
        if len(have) < len(want):
            return False
        have = have[:len(want)]
    if len(want) != len(have):
        return False
    return all(w in ("*", h) for w, h in zip(want, have))


def rule_for(rules, method: str, path: str):
    return next((rule for rule in rules if rule[0] == method and matches(rule[1], path)), None)


def mark_anonymous(paths: dict) -> list:
    marked, used = [], set()
    for path, item in paths.items():
        for method, operation in item.items():
            if not isinstance(operation, dict):
                continue  # path-level "parameters", "summary", ...
            verb = method.upper()
            rule = rule_for(ANONYMOUS, verb, path)
            if rule and not rule_for(AUTHENTICATED, verb, path):
                operation[AUTH_TYPE_KEY] = AUTH_TYPE_NONE
                operation["security"] = []
                marked.append(f"{verb} {path}")
                used.add(rule)

    # A rule that matches nothing means a route was renamed or removed. Fail
    # loudly rather than quietly publishing a contract that 401s it.
    for verb, pattern in ANONYMOUS:
        if (verb, pattern) not in used:
            raise SystemExit(f"ANONYMOUS rule {verb} {pattern} matches no operation")
    return marked


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    source, dest = sys.argv[1], sys.argv[2]
    with open(source, encoding="utf-8") as handle:
        doc = json.load(handle)

    paths = doc.get("paths", {})
    doc["paths"] = {strip_prefix(path): item for path, item in paths.items()}
    anonymous = mark_anonymous(doc["paths"])

    # WSO2 rejects a definition whose servers point somewhere else; the gateway
    # supplies the endpoint from its own configuration.
    doc.pop("servers", None)

    with open(dest, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(doc, handle, indent=2, sort_keys=True)
        handle.write("\n")

    stripped = sum(1 for path in paths if path.startswith(PREFIX))
    print(f"  {len(doc['paths'])} paths ({stripped} had the /api prefix removed)")
    print(f"  {len(anonymous)} operation(s) anonymous at the gateway:")
    for entry in sorted(anonymous):
        print(f"    {entry}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
