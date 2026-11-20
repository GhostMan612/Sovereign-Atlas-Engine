# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
from __future__ import annotations

import base64
import binascii
import hashlib
import json
import re
from datetime import datetime, timedelta, timezone

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey


class ProvenanceDenied(ValueError):
    pass


PAYLOAD_TYPE = "application/vnd.in-toto+json"
PREDICATE_TYPE = "https://sovereignatlas.example/provenance/v1"
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
SHA1 = re.compile(r"[0-9a-f]{40}\Z")


def _pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ProvenanceDenied("duplicate JSON key")
        result[key] = value
    return result


def _json(data):
    if not isinstance(data, bytes) or not data or len(data) > 8 * 1024 * 1024:
        raise ProvenanceDenied("invalid JSON size")
    try:
        result = json.loads(data, object_pairs_hook=_pairs, parse_constant=lambda _: (_ for _ in ()).throw(ProvenanceDenied("non-finite JSON")))
    except (ValueError, UnicodeError, RecursionError) as error:
        raise ProvenanceDenied("invalid JSON") from error
    if not isinstance(result, dict):
        raise ProvenanceDenied("JSON object required")
    return result


def _decode(value):
    try:
        return base64.b64decode(value, validate=True)
    except (TypeError, ValueError, binascii.Error) as error:
        raise ProvenanceDenied("invalid base64") from error


def _time(value):
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ProvenanceDenied("UTC timestamp required")
    try:
        return datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError as error:
        raise ProvenanceDenied("invalid timestamp") from error


def verify_provenance(envelope_bytes, policy, candidate_digest, files, now=None):
    if not isinstance(policy, dict) or set(policy) != {"repository", "source_ref", "approved_source_commits", "builders"}:
        raise ProvenanceDenied("invalid trust policy")
    if not isinstance(candidate_digest, str) or not SHA256.fullmatch(candidate_digest):
        raise ProvenanceDenied("invalid candidate digest")
    if not isinstance(files, dict) or not files or any(not isinstance(k, str) or not k or not isinstance(v, str) or not SHA256.fullmatch(v) for k, v in files.items()):
        raise ProvenanceDenied("invalid file inventory")
    if not isinstance(policy["repository"], str) or not policy["repository"] or not isinstance(policy["source_ref"], str) or not policy["source_ref"]:
        raise ProvenanceDenied("invalid source policy")
    commits, builders = policy["approved_source_commits"], policy["builders"]
    if not isinstance(commits, list) or not commits or any(not isinstance(x, str) or not SHA1.fullmatch(x) for x in commits) or not isinstance(builders, dict) or not builders:
        raise ProvenanceDenied("no trusted source or builders")
    envelope = _json(envelope_bytes)
    if set(envelope) != {"payloadType", "payload", "signatures"} or envelope["payloadType"] != PAYLOAD_TYPE:
        raise ProvenanceDenied("invalid DSSE envelope")
    payload_bytes = _decode(envelope["payload"])
    statement = _json(payload_bytes)
    signatures = envelope["signatures"]
    if not isinstance(signatures, list) or len(signatures) != 1 or not isinstance(signatures[0], dict) or set(signatures[0]) != {"keyid", "sig"}:
        raise ProvenanceDenied("exactly one builder signature required")
    if set(statement) != {"_type", "subject", "predicateType", "predicate"} or statement["_type"] != "https://in-toto.io/Statement/v1" or statement["predicateType"] != PREDICATE_TYPE:
        raise ProvenanceDenied("invalid statement")
    predicate = statement["predicate"]
    expected = {"builder", "repository", "source_ref", "source_sha", "candidate_sha256", "build_id", "issued_at", "expires_at"}
    if not isinstance(predicate, dict) or set(predicate) != expected:
        raise ProvenanceDenied("invalid predicate")
    builder = predicate["builder"]
    if not isinstance(builder, str) or builder not in builders or not isinstance(builders[builder], dict) or set(builders[builder]) != {"keyid", "ed25519_public_key_b64"}:
        raise ProvenanceDenied("untrusted builder")
    trust = builders[builder]
    key = _decode(trust["ed25519_public_key_b64"])
    keyid = hashlib.sha256(key).hexdigest()
    if len(key) != 32 or trust["keyid"] != keyid or signatures[0]["keyid"] != keyid:
        raise ProvenanceDenied("builder key identity mismatch")
    payload_type = PAYLOAD_TYPE.encode("utf-8")
    pae = b"DSSEv1 " + str(len(payload_type)).encode() + b" " + payload_type + b" " + str(len(payload_bytes)).encode() + b" " + payload_bytes
    try:
        Ed25519PublicKey.from_public_bytes(key).verify(_decode(signatures[0]["sig"]), pae)
    except (InvalidSignature, ValueError) as error:
        raise ProvenanceDenied("invalid builder signature") from error
    if predicate["repository"] != policy["repository"] or predicate["source_ref"] != policy["source_ref"] or predicate["source_sha"] not in commits or predicate["candidate_sha256"] != candidate_digest:
        raise ProvenanceDenied("source or candidate mismatch")
    if not isinstance(predicate["build_id"], str) or not predicate["build_id"].strip():
        raise ProvenanceDenied("build identity missing")
    issued, expires = _time(predicate["issued_at"]), _time(predicate["expires_at"])
    instant = now or datetime.now(timezone.utc)
    if instant.tzinfo is None or issued > instant + timedelta(minutes=5) or expires <= instant or expires <= issued or expires - issued > timedelta(days=7):
        raise ProvenanceDenied("provenance outside validity window")
    subjects = statement["subject"]
    if not isinstance(subjects, list) or len(subjects) != len(files):
        raise ProvenanceDenied("subject inventory mismatch")
    observed = {}
    for subject in subjects:
        if not isinstance(subject, dict) or set(subject) != {"name", "digest"} or not isinstance(subject["name"], str) or subject["name"] in observed or not isinstance(subject["digest"], dict) or set(subject["digest"]) != {"sha256"}:
            raise ProvenanceDenied("invalid subject")
        observed[subject["name"]] = subject["digest"]["sha256"]
    if observed != files:
        raise ProvenanceDenied("subject digest mismatch")
    return {"builder": builder, "source_sha": predicate["source_sha"], "build_id": predicate["build_id"]}
