# Phase 0.4 — Adversarial Fixture Catalog (Normative)

- **Status:** PROPOSED. Files in `test/golden/adversarial/`.
- **Rule:** every fixture specifies input, expected classification, error category, whether rejection
  is mandatory, whether normalization is allowed, and whether silent coercion is prohibited.
  Error categories only (no code enums — DEC-006 open).

| ID | Input | Expected | Category | Reject mandatory? | Normalize? | Silent coercion? |
|---|---|---|---|---|---|---|
| ADV-001 | null coordinate object | reject | INVALID_COORDINATE | yes | no | prohibited |
| ADV-002 | missing latitude | reject | INVALID_COORDINATE | yes | no | prohibited |
| ADV-003 | missing longitude | reject | INVALID_COORDINATE | yes | no | prohibited |
| ADV-004 | malformed number ("12.3.4") | reject | MALFORMED | yes | no | prohibited |
| ADV-005 | NaN latitude ("NaN") | reject | NON_FINITE | yes | no | prohibited |
| ADV-006 | +Inf longitude ("Infinity") | reject | NON_FINITE | yes | no | prohibited |
| ADV-007 | lat 91 / -91 | reject | OUT_OF_RANGE | yes | no | prohibited |
| ADV-008 | lon 181 pre-normalization | reject-or-normalize per DEC-004 (fixture records BOTH allowed outcomes pending decision; silent choice prohibited) | OUT_OF_RANGE | conditional | pending DEC-004 | prohibited silently |
| ADV-009 | zoom -1 / 25 | reject | INVALID_ZOOM | yes | no | prohibited |
| ADV-010 | empty geometry collection use | per DEC-007 (reject-or-accept documented; silent reinterpret prohibited) | INVALID_GEOMETRY | conditional | no | prohibited |
| ADV-011 | unclosed ring | per DEC-007 (auto-close vs reject pending) | INVALID_GEOMETRY | conditional | pending | prohibited silently |
| ADV-012 | <2-pt linestring / <3-pt polygon | reject member, keep collection (SOURCE-VERIFIED skip precedent) | INVALID_GEOMETRY | member yes / collection no | no | prohibited |
| ADV-013 | empty feature collection | accept as empty (PROPOSED) | — | no | — | — |
| ADV-014 | duplicate feature IDs | flag DUPLICATE_IDENTITY (resolution DECISION REQUIRED DEC-013) | CONFLICT | flag yes / abort no | no | prohibited |
| ADV-015 | conflicting identities (same id, different geometry) | flag CONFLICT; precedence by authority (ATLAS-AUTH-001) | CONFLICT | flag yes | no | prohibited |
| ADV-016 | inverted layer order | reject order / re-sort explicitly (silent reorder prohibited) | INVALID_ORDER | yes | explicit-only | prohibited silently |
| ADV-017 | malformed tile identity | reject | MALFORMED | yes | no | prohibited |
| ADV-018 | malformed provenance (missing source) | flag INCOMPLETE_PROVENANCE; treat authority UNKNOWN | INVALID_PROVENANCE | flag yes | no | prohibited (never default to authoritative) |
| ADV-019 | unsupported CRS tag | reject | UNSUPPORTED_CRS | yes | no (no silent WGS84 assumption) | prohibited |
| ADV-020 | corrupt camera string | reject → adapter fallback (core surfaces error; SOURCE-VERIFIED guard precedent) | MALFORMED | yes (core) | no | prohibited |
| ADV-021 | corrupt cache bytes | delete + refetch (SOURCE-VERIFIED self-recovery) | CORRUPT_ENTRY | recover yes | n/a | n/a |
| ADV-022 | zero-length migration flow | reject | INVALID_GEOMETRY | yes | no | prohibited |
| ADV-023 | H3-claimed-as-authoritative | reject claim (derived MUST NOT self-promote) | AUTHORITY_MISMATCH | yes | no | prohibited |
