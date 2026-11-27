# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
import base64
import hashlib
import json
import unittest
from datetime import datetime, timezone

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from atlas_publisher.provenance import PAYLOAD_TYPE, PREDICATE_TYPE, ProvenanceDenied, verify_provenance


class ProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.key = Ed25519PrivateKey.generate()
        public = self.key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        self.keyid = hashlib.sha256(public).hexdigest()
        self.commit = "a" * 40
        self.candidate = "b" * 64
        self.files = {"artifacts/a": "c" * 64, "evidence/a": "d" * 64}
        self.now = datetime(2026, 11, 15, tzinfo=timezone.utc)
        self.policy = {"repository": "example/repo", "source_ref": "refs/heads/main", "approved_source_commits": [self.commit], "builders": {"builder-1": {"keyid": self.keyid, "ed25519_public_key_b64": base64.b64encode(public).decode()}}}
        self.statement = {"_type": "https://in-toto.io/Statement/v1", "predicateType": PREDICATE_TYPE, "subject": [{"name": name, "digest": {"sha256": digest}} for name, digest in self.files.items()], "predicate": {"builder": "builder-1", "repository": "example/repo", "source_ref": "refs/heads/main", "source_sha": self.commit, "candidate_sha256": self.candidate, "build_id": "run-1", "issued_at": "2026-11-14T23:00:00Z", "expires_at": "2026-11-16T00:00:00Z"}}

    def envelope(self, key=None):
        payload = json.dumps(self.statement, separators=(",", ":")).encode()
        typ = PAYLOAD_TYPE.encode()
        pae = b"DSSEv1 " + str(len(typ)).encode() + b" " + typ + b" " + str(len(payload)).encode() + b" " + payload
        return json.dumps({"payloadType": PAYLOAD_TYPE, "payload": base64.b64encode(payload).decode(), "signatures": [{"keyid": self.keyid, "sig": base64.b64encode((key or self.key).sign(pae)).decode()}]}).encode()

    def verify(self, envelope=None):
        return verify_provenance(envelope or self.envelope(), self.policy, self.candidate, self.files, self.now)

    def test_valid_signature_and_binding(self):
        self.assertEqual(self.verify()["source_sha"], self.commit)

    def test_self_issued_key_denied(self):
        with self.assertRaises(ProvenanceDenied):
            self.verify(self.envelope(Ed25519PrivateKey.generate()))

    def test_wrong_repository_denied(self):
        self.statement["predicate"]["repository"] = "attacker/repo"
        with self.assertRaises(ProvenanceDenied):
            self.verify()

    def test_unapproved_revision_denied(self):
        self.statement["predicate"]["source_sha"] = "e" * 40
        with self.assertRaises(ProvenanceDenied):
            self.verify()

    def test_candidate_replay_denied(self):
        self.statement["predicate"]["candidate_sha256"] = "e" * 64
        with self.assertRaises(ProvenanceDenied):
            self.verify()

    def test_changed_evidence_denied(self):
        self.statement["subject"][1]["digest"]["sha256"] = "e" * 64
        with self.assertRaises(ProvenanceDenied):
            self.verify()

    def test_missing_or_duplicate_subject_denied(self):
        self.statement["subject"][1] = self.statement["subject"][0]
        with self.assertRaises(ProvenanceDenied):
            self.verify()

    def test_expired_denied(self):
        self.statement["predicate"]["expires_at"] = "2026-11-14T23:30:00Z"
        with self.assertRaises(ProvenanceDenied):
            self.verify()

    def test_duplicate_envelope_key_denied(self):
        with self.assertRaises(ProvenanceDenied):
            self.verify(b'{"payloadType":"a","payloadType":"b"}')


if __name__ == "__main__":
    unittest.main()
