# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from .handoff import HandoffDenied, snapshot
from .provenance import ProvenanceDenied, _json, verify_provenance


FILES = {
    "play_unsigned_apk": "artifacts/play-unsigned.apk",
    "enterprise_unsigned_apk": "artifacts/enterprise-unsigned.apk",
    "sdk_jar": "artifacts/atlas-sdk.jar",
    "dependencies": "evidence/dependencies.json",
    "licenses": "evidence/licenses.json",
    "source_tree": "evidence/source-tree.json",
    "meshtastic_pin": "evidence/meshtastic-pin.json",
    "tak_rights": "evidence/tak-rights.json",
    "ownership": "evidence/ownership.json",
    "release_disposition": "evidence/release-disposition.json",
}


def inspect(candidate, manifest_digest, trust, envelope):
    manifest_snapshot = snapshot(candidate, "candidate.json", manifest_digest, 8 * 1024 * 1024)
    fd = manifest_snapshot.consume()
    try:
        data = bytearray()
        while True:
            block = os.read(fd, 1024 * 1024)
            if not block:
                break
            data.extend(block)
        manifest = _json(bytes(data))
    finally:
        os.close(fd)
    if set(manifest) != {"schema", "repository", "source_ref", "source_sha", "meshtastic_pin", "files"} or manifest["schema"] != "sovereign-atlas-candidate/v1":
        raise ProvenanceDenied("invalid candidate manifest")
    entries = manifest["files"]
    if not isinstance(entries, list) or len(entries) != len(FILES):
        raise ProvenanceDenied("candidate inventory incomplete")
    inventory = {}
    sizes = {}
    roles = set()
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != {"role", "path", "sha256", "bytes"}:
            raise ProvenanceDenied("invalid candidate entry")
        role = entry["role"]
        if not isinstance(role, str) or role not in FILES or role in roles or entry["path"] != FILES[role] or type(entry["bytes"]) is not int or not 0 < entry["bytes"] <= 2 * 1024 * 1024 * 1024:
            raise ProvenanceDenied("invalid candidate role or size")
        roles.add(role)
        inventory[entry["path"]] = entry["sha256"]
        sizes[entry["path"]] = entry["bytes"]
    result = verify_provenance(envelope, trust, manifest_digest, inventory)
    if manifest["repository"] != trust["repository"] or manifest["source_ref"] != trust["source_ref"] or manifest["source_sha"] != result["source_sha"]:
        raise ProvenanceDenied("manifest source differs from attestation")
    for path, digest in inventory.items():
        item = snapshot(candidate, path, digest, sizes[path])
        try:
            if item.size != sizes[path]:
                raise ProvenanceDenied("candidate size mismatch")
        finally:
            item.close()
    return result


def main():
    parser = argparse.ArgumentParser(description="Review-only inspection; release is always denied")
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--manifest-sha256", required=True)
    parser.add_argument("--trust-policy", type=Path, required=True)
    parser.add_argument("--provenance", type=Path, required=True)
    args = parser.parse_args()
    try:
        trust = _json(args.trust_policy.read_bytes())
        inspect(args.candidate, args.manifest_sha256, trust, args.provenance.read_bytes())
        print("Inspection completed. DENY: independent approval and licensing clearance unavailable.", file=sys.stderr)
    except (ProvenanceDenied, HandoffDenied, OSError, TypeError, ValueError, KeyError, RecursionError) as error:
        print("DENY: " + str(error), file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
