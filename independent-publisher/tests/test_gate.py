# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
import hashlib
import json
import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from atlas_publisher.gate import FILES, inspect, main
from atlas_publisher.handoff import HandoffDenied
from atlas_publisher.provenance import ProvenanceDenied
import test_provenance as fixtures


class GateIntegrationTest(unittest.TestCase):
    envelope = fixtures.ProvenanceTest.envelope

    def setUp(self):
        fixture = fixtures.ProvenanceTest()
        fixture.setUp()
        for name in ("key", "keyid", "commit", "candidate", "files", "now", "policy", "statement"):
            setattr(self, name, getattr(fixture, name))
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.files = {}
        entries = []
        for role, relative in FILES.items():
            path = self.root / relative
            path.parent.mkdir(exist_ok=True)
            data = (role + " contents").encode()
            path.write_bytes(data)
            path.chmod(0o600)
            digest = hashlib.sha256(data).hexdigest()
            self.files[relative] = digest
            entries.append({"role": role, "path": relative, "sha256": digest, "bytes": len(data)})
        manifest = {"schema": "sovereign-atlas-candidate/v1", "repository": "example/repo", "source_ref": "refs/heads/main", "source_sha": self.commit, "meshtastic_pin": "pin", "files": entries}
        data = json.dumps(manifest).encode()
        (self.root / "candidate.json").write_bytes(data)
        (self.root / "candidate.json").chmod(0o600)
        self.candidate = hashlib.sha256(data).hexdigest()
        now = datetime.now(timezone.utc)
        self.statement["subject"] = [{"name": name, "digest": {"sha256": digest}} for name, digest in self.files.items()]
        self.statement["predicate"].update({"candidate_sha256": self.candidate, "issued_at": (now - timedelta(minutes=1)).isoformat().replace("+00:00", "Z"), "expires_at": (now + timedelta(hours=1)).isoformat().replace("+00:00", "Z")})

    def test_inspection_binds_authenticated_inventory(self):
        self.assertEqual(inspect(str(self.root), self.candidate, self.policy, self.envelope())["source_sha"], self.commit)

    def test_changed_candidate_bytes_denied(self):
        (self.root / FILES["dependencies"]).write_bytes(b"substituted")
        with self.assertRaises(HandoffDenied):
            inspect(str(self.root), self.candidate, self.policy, self.envelope())

    def test_policy_key_substitution_denied(self):
        self.policy["builders"]["builder-1"]["keyid"] = "0" * 64
        with self.assertRaises(ProvenanceDenied):
            inspect(str(self.root), self.candidate, self.policy, self.envelope())

    def test_manifest_substitution_denied(self):
        (self.root / "candidate.json").write_bytes(b"{}")
        with self.assertRaises(HandoffDenied):
            inspect(str(self.root), self.candidate, self.policy, self.envelope())

    def test_cli_denies_after_successful_inspection(self):
        policy_file = self.root / "policy.json"
        envelope_file = self.root / "provenance.json"
        policy_file.write_text(json.dumps(self.policy), encoding="utf-8")
        envelope_file.write_bytes(self.envelope())
        argv = ["gate", "--candidate", str(self.root), "--manifest-sha256", self.candidate, "--trust-policy", str(policy_file), "--provenance", str(envelope_file)]
        with patch("sys.argv", argv):
            self.assertEqual(main(), 2)


if __name__ == "__main__":
    unittest.main()
