"""Prepares the booking service's OpenAPI document for import into WSO2.

The gateway publishes the API at context ``/api``, so the paths in the
definition have to be relative to that — otherwise every route would be served
at ``/api/api/...``. Springdoc emits them with the prefix included, so strip it
here rather than expecting anyone to remember.

Output is pretty-printed and key-sorted so the committed file diffs readably.

    python normalise-openapi.py <source.json> <dest.json>
"""

import json
import sys

PREFIX = "/api"


def strip_prefix(path: str) -> str:
    if path == PREFIX:
        return "/"
    if path.startswith(PREFIX + "/"):
        return path[len(PREFIX):]
    return path


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    source, dest = sys.argv[1], sys.argv[2]
    with open(source, encoding="utf-8") as handle:
        doc = json.load(handle)

    paths = doc.get("paths", {})
    doc["paths"] = {strip_prefix(path): item for path, item in paths.items()}

    # WSO2 rejects a definition whose servers point somewhere else; the gateway
    # supplies the endpoint from its own configuration.
    doc.pop("servers", None)

    with open(dest, "w", encoding="utf-8") as handle:
        json.dump(doc, handle, indent=2, sort_keys=True)
        handle.write("\n")

    stripped = sum(1 for path in paths if path.startswith(PREFIX))
    print(f"  {len(doc['paths'])} paths ({stripped} had the /api prefix removed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
